# MedTrack Inpatient Clinical UI Prototype (Google Native Theme)

This directory contains the complete, workable, and navigable HTML prototype of MedTrack's inpatient clinical operating system, styled with **Google Native Material Design 3 (Material You)**.

## Files

- [index.html](file:///e:/01_Coding/14_MedTrack/spec/mockup/index.html) (Default web entry point)
- [medtrack_ui_prototype.html](file:///e:/01_Coding/14_MedTrack/spec/mockup/medtrack_ui_prototype.html) (Identical prototype file)

## How to Run

Simply double-click `index.html` or `medtrack_ui_prototype.html` to open it in any modern web browser (Chrome, Edge, Firefox, Safari). No web server, Node.js, or build step required.

## Design System

- **Surfaces**: Predominantly clean white (`#FFFFFF` on `#F8F9FA`) with hairline dividers (`#E0E2E6`). Designed for high contrast and glare resistance under bright hospital fluorescent ward lights.
- **Brand & Accent Colors**:
  - **Google Blue (`#1A73E8`)**: Active tabs, primary buttons, main ward titles. Container: `#E8F0FE`.
  - **Google Red (`#D93025`)**: High-acuity clinical warnings (Severe Sepsis), overdue tasks, stopped medications. Container: `#FCE8E6`.
  - **Google Yellow / Amber (`#F9AB00` / `#B06000`)**: ICU bed badges, tasks due within 2 hours, intermediate acuity. Container: `#FEF7E0`.
  - **Google Green (`#188038` / `#137333`)**: Confirmed diagnoses, stable vitals, completed task checkmarks. Container: `#E6F4EA`.
  - **Google Purple (`#7627BB`)**: Specialist consults and outgoing referrals. Container: `#F3E8FD`.
- **Navigation**: Material 3 bottom navigation bar with rounded pill indicators behind active destinations.

## Navigable Clinical Workflows

1. **Inpatient Census (Screen 1)**:
   - Live search bar filtering across patient name, bed, UHID, and diagnosis.
   - Segment pills for `Primary (12)`, `Referrals (6)`, `On-Call`, and `Discharged`.
   - Collapsible ward sections: Floor 3 (ICU), Floor 4 (Female Med), Floor 5 (Male Med).
   - Clicking any patient card opens their complete clinical workspace.

2. **Ward-Centric Rounds Queue (Screen 2)**:
   - Tasks grouped by floor to eliminate "Floor Blindness" (leaving a floor before finishing its tasks).
   - Click `Done + Note` to mark a task complete, decrement the pending counter, and write a completion event to the patient's timeline.
   - `+2h Snooze` button to reschedule tasks.

3. **Patient Inpatient Hub (Screen 3)**:
   - Multi-tab dossier: Overview, Timeline, Medications, Tasks, and Reports.
   - Differentiates active prescription orders from held/discontinued medications with explicit rationale.
   - `Move Bed` button launches an interactive transfer modal to simulate moving patients between wards.
   - Scanned report items open a decrypted report viewer modal.

4. **Human-in-the-Loop AI Action Gate (Screen 4)**:
   - Demonstrates converting unstructured doctor speech/dictation into 4 checkable atomic actions (Transfer, Discontinue drug, Order drug, Timed reminder).
   - Clicking `Confirm & Commit to Record` updates the in-memory patient record, reflects in the timeline, and commits changes atomically.

5. **Referral & Unassigned Inbox (Screen 5)**:
   - Triage for informal WhatsApp and phone call referrals without creating duplicate patient records.

6. **Shift Handover (Screen 6)**:
   - Auto-compiled summary for the on-call/night resident.
   - Working `Copy for WhatsApp` button that writes formatted markdown directly to the system clipboard.
