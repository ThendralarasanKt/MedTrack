"""Workbench compatibility shim. Routing policy lives in the backend orchestrator."""

from medtrack_gateway.orchestration.routing_policy import noul_value, select_routes

__all__ = ["noul_value", "select_routes"]
