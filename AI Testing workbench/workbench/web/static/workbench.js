(() => {
  const caseSelect = document.getElementById("caseSelect");
  const inputText = document.getElementById("inputText");
  const fixtureTag = document.getElementById("fixtureTag");
  const fixtureLabel = document.getElementById("fixtureLabel");
  const contextJson = document.getElementById("contextJson");
  const runMode = document.getElementById("runMode");
  const runBtn = document.getElementById("runBtn");
  const runStatus = document.getElementById("runStatus");
  const modeBanner = document.getElementById("modeBanner");
  const configInspect = document.getElementById("configInspect");
  const keyHint = document.getElementById("keyHint");
  const traceList = document.getElementById("traceList");
  const traceMeta = document.getElementById("traceMeta");
  const routingSummary = document.getElementById("routingSummary");
  const routingDetail = document.getElementById("routingDetail");
  const usageSummary = document.getElementById("usageSummary");
  const usageDetail = document.getElementById("usageDetail");
  const proposalSummary = document.getElementById("proposalSummary");
  const operations = document.getElementById("operations");
  const reviewBtn = document.getElementById("reviewBtn");
  const reviewDecision = document.getElementById("reviewDecision");
  const reviewOpId = document.getElementById("reviewOpId");
  const reviewComment = document.getElementById("reviewComment");
  const reviewNote = document.getElementById("reviewNote");
  const reviewList = document.getElementById("reviewList");

  let currentRunId = null;
  let keyReady = false;

  function selectedOption() {
    return caseSelect.options[caseSelect.selectedIndex];
  }

  function loadCase() {
    const opt = selectedOption();
    if (!opt) return;
    inputText.value = opt.dataset.text || "";
    fixtureTag.value = opt.dataset.fixture || "";
    try {
      const ctx = JSON.parse(opt.dataset.context || "{}");
      contextJson.value = JSON.stringify(ctx, null, 2);
    } catch {
      contextJson.value = "{}";
    }
  }

  function syncModeUi() {
    const live = runMode.value === "LIVE_SYNTHETIC";
    fixtureLabel.style.display = live ? "none" : "";
    if (live) {
      modeBanner.textContent = "LIVE PROVIDER — SYNTHETIC DATA ONLY";
      runBtn.textContent = "Run live synthetic";
    } else {
      modeBanner.textContent = keyReady
        ? "LIVE PROVIDER AVAILABLE — default still MOCK"
        : "MOCK — NO PROVIDER CALLS";
      runBtn.textContent = "Run mock pipeline";
    }
  }

  function stageDetail(run, name) {
    return (run.trace || []).find((s) => s.stage === name)?.detail || null;
  }

  function renderRouting(run) {
    const routing = stageDetail(run, "routing");
    const modelCfg = stageDetail(run, "model_configuration");
    if (!routing) {
      routingSummary.textContent = "No routing stage in this run.";
      routingDetail.textContent = "";
      return;
    }
    const primary = routing.primary || {};
    routingSummary.textContent = `Primary ${primary.capability || "?"} — ${
      primary.reason || ""
    }. Model route ${routing.modelRouteId || "none"}.`;
    routingDetail.textContent = JSON.stringify(
      {
        policyVersion: routing.policyVersion,
        primary,
        matched: routing.matched,
        reasons: routing.reasons,
        fixtureOverride: routing.fixtureOverride,
        modelConfiguration: modelCfg,
      },
      null,
      2
    );
  }

  function renderUsage(run) {
    const usage = run.usage || stageDetail(run, "usage_summary");
    if (!usage) {
      usageSummary.textContent = "No usage recorded for this run.";
      usageDetail.textContent = "";
      return;
    }
    const totals = usage.totals || {};
    const cost =
      totals.costUsd === null || totals.costUsd === undefined
        ? "cost n/a"
        : `$${Number(totals.costUsd).toFixed(6)}`;
    const models = (totals.models || []).join(", ") || "—";
    usageSummary.textContent = `${totals.providerCalls || 0} provider call(s) · models ${models} · ${
      totals.totalTokens || 0
    } tokens · ${totals.latencyMs || 0} ms · ${cost}`;
    usageDetail.textContent = JSON.stringify(usage, null, 2);
  }

  function renderTrace(run) {
    traceList.innerHTML = "";
    (run.trace || []).forEach((stage) => {
      const li = document.createElement("li");
      const mark = document.createElement("span");
      mark.className = stage.ok ? "ok" : "bad";
      mark.textContent = stage.ok ? "OK" : "FAIL";
      li.appendChild(mark);
      li.appendChild(document.createTextNode(` ${stage.stage}`));
      const detail = document.createElement("pre");
      detail.className = "meta";
      detail.textContent = JSON.stringify(stage.detail, null, 2);
      li.appendChild(detail);
      traceList.appendChild(li);
    });
    traceMeta.textContent = JSON.stringify(
      {
        id: run.id,
        status: run.status,
        mode: run.mode,
        fixtureTag: run.fixtureTag,
        clinicalWriteClaimed: run.clinicalWriteClaimed,
        androidWrite: false,
      },
      null,
      2
    );
  }

  function renderProposal(run) {
    const proposal = run.proposal;
    operations.innerHTML = "";
    if (!proposal) {
      proposalSummary.textContent = run.error
        ? `No proposal. Error: ${run.error.code || ""} ${run.error.message || JSON.stringify(run.error)}`
        : "No proposal object.";
      return;
    }
    proposalSummary.textContent = `${proposal.summary || "(no summary)"} — identity ${
      (proposal.identity && proposal.identity.state) || "?"
    }. Validation ${run.validation && run.validation.ok ? "passed" : "blocked/failed"}.`;
    (proposal.operations || []).forEach((op) => {
      const card = document.createElement("div");
      card.className = "op-card";
      card.innerHTML = `<h3>${op.type || "OP"} <span class="muted">${op.operationId || ""}</span></h3>`;
      const pre = document.createElement("pre");
      pre.textContent = JSON.stringify(op, null, 2);
      card.appendChild(pre);
      operations.appendChild(card);
    });
    if (proposal.clarification) {
      const note = document.createElement("p");
      note.className = "muted";
      note.textContent = `Clarification: ${proposal.clarification}`;
      operations.prepend(note);
    }
  }

  function renderReviews(run) {
    reviewList.innerHTML = "";
    (run.reviews || []).forEach((r) => {
      const li = document.createElement("li");
      li.textContent = `${r.decision}${r.operationId ? " @ " + r.operationId : ""} — ${r.comment || ""}`;
      reviewList.appendChild(li);
    });
  }

  async function loadConfig() {
    const [statusRes, configRes] = await Promise.all([
      fetch("/workbench/api/status"),
      fetch("/workbench/api/config"),
    ]);
    const status = await statusRes.json();
    const config = await configRes.json();
    keyReady = Boolean(status.openRouterKeyConfigured || status.providerAvailable);
    const liveOpt = runMode.querySelector('option[value="LIVE_SYNTHETIC"]');
    if (liveOpt) {
      liveOpt.disabled = !keyReady;
      liveOpt.textContent = keyReady
        ? "LIVE_SYNTHETIC"
        : "LIVE_SYNTHETIC (needs OPENROUTER_API_KEY)";
    }
    if (!keyReady && runMode.value === "LIVE_SYNTHETIC") {
      runMode.value = "MOCK";
    }
    configInspect.textContent = JSON.stringify(config, null, 2);
    if (keyHint) {
      keyHint.textContent = keyReady
        ? "OpenRouter key detected. You can select LIVE_SYNTHETIC for provider calls on synthetic cases."
        : "OpenRouter key not set. Stay on MOCK, or set OPENROUTER_API_KEY and restart when ready for live testing.";
    }
    syncModeUi();
  }

  async function runCase() {
    const caseId = caseSelect.value;
    const mode = runMode.value;
    runStatus.textContent = "Running…";
    runBtn.disabled = true;
    reviewBtn.disabled = true;
    let context = {};
    try {
      context = JSON.parse(contextJson.value || "{}");
    } catch {
      runStatus.textContent = "Context JSON is invalid.";
      runBtn.disabled = false;
      return;
    }
    const body = {
      mode,
      dataClassification: "SYNTHETIC",
      inputText: inputText.value,
      context,
    };
    if (mode === "MOCK") {
      body.fixtureTag = fixtureTag.value || null;
    }
    const res = await fetch(`/workbench/api/cases/${encodeURIComponent(caseId)}/run`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    runBtn.disabled = false;
    if (!res.ok) {
      runStatus.textContent = data.error?.message || "Run failed";
      return;
    }
    currentRunId = data.id;
    runStatus.textContent = `Run ${data.status} (${data.id}) · ${data.mode}`;
    if (data.banner) {
      modeBanner.textContent = data.banner;
    }
    renderRouting(data);
    renderUsage(data);
    renderTrace(data);
    renderProposal(data);
    renderReviews(data);
    reviewBtn.disabled = false;
    reviewNote.textContent =
      "Simulate approval records evaluation only; Android CareWritePath is not invoked.";
  }

  async function saveReview() {
    if (!currentRunId) return;
    const res = await fetch(`/workbench/api/case-runs/${encodeURIComponent(currentRunId)}/review`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        decision: reviewDecision.value,
        operationId: reviewOpId.value || null,
        comment: reviewComment.value || null,
        reviewer: "local-evaluator",
      }),
    });
    const data = await res.json();
    if (!res.ok) {
      reviewNote.textContent = data.error?.message || "Review failed";
      return;
    }
    reviewNote.textContent = data.note || "Saved evaluation review.";
    const runRes = await fetch(`/workbench/api/runs/${encodeURIComponent(currentRunId)}`);
    const run = await runRes.json();
    renderReviews(run);
  }

  caseSelect.addEventListener("change", loadCase);
  runMode.addEventListener("change", syncModeUi);
  runBtn.addEventListener("click", runCase);
  reviewBtn.addEventListener("click", saveReview);
  loadCase();
  loadConfig();
  syncModeUi();
})();
