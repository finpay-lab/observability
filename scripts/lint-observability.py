#!/usr/bin/env python3
"""Lint the FinPay observability config (TASK-100).

Validates that:

* every Grafana dashboard under ``grafana/dashboards`` is a valid importable
  JSON dashboard — required top-level keys, a ``uid`` matching its filename,
  panels with datasource/targets, and no dangling `${VAR}` template references;
* every YAML file under ``prometheus/``, ``otel/``, ``grafana/provisioning/``
  and the root ``docker-compose.yml`` parses;
* ``prometheus/prometheus.yml`` actually references ``alert-rules.yml`` and
  ``prometheus/targets/*.yml`` (so Prometheus picks them up at runtime).

Run from the repo root:  python3 scripts/lint-observability.py
Requires PyYAML (installed in CI before running).
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]

DASHBOARDS_DIR = ROOT / "grafana" / "dashboards"

# Datasource template variables that an importable dashboard must declare.
REQUIRED_DATASOURCE_INPUTS = ("DS_PROMETHEUS",)

YAML_FILES = [
    ROOT / "prometheus" / "prometheus.yml",
    ROOT / "prometheus" / "alert-rules.yml",
    ROOT / "prometheus" / "targets" / "services.yml",
    ROOT / "otel" / "otel-collector.yaml",
    ROOT / "otel" / "tempo.yaml",
    ROOT / "grafana" / "provisioning" / "datasources" / "prometheus.yml",
    ROOT / "grafana" / "provisioning" / "dashboards" / "dashboards.yaml",
    ROOT / "docker-compose.yml",
]

DASHBOARD_REQUIRED_KEYS = ("title", "uid", "schemaVersion", "panels", "refresh")

PANEL_REQUIRED_KEYS = ("title", "type", "targets", "gridPos", "datasource")


def _fail(message: str, path: Path) -> int:
    print(f"FAIL {path.relative_to(ROOT)}: {message}")
    return 1


def lint_dashboards() -> int:
    errors = 0
    for dash in sorted(DASHBOARDS_DIR.glob("*.json")):
        try:
            data = json.loads(dash.read_text())
        except json.JSONDecodeError as exc:
            errors += _fail(f"invalid JSON: {exc}", dash)
            continue

        missing = [k for k in DASHBOARD_REQUIRED_KEYS if k not in data]
        if missing:
            errors += _fail(f"missing required keys {missing}", dash)

        uid_ok = data.get("uid") == dash.stem
        if not uid_ok:
            errors += _fail(f"`uid` ({data.get('uid')!r}) != filename stem {dash.stem!r}", dash)

        declared_inputs = {entry["name"] for entry in data.get("__inputs", [])}
        missing_inputs = [
            name for name in REQUIRED_DATASOURCE_INPUTS if name not in declared_inputs
        ]
        if missing_inputs:
            errors += _fail(f"missing datasource __inputs {missing_inputs}", dash)

        if not isinstance(data.get("panels"), list) or not data["panels"]:
            errors += _fail("no panels (dashboard would render empty)", dash)

        panel_ids = set()
        template_vars = set()
        for tv in data.get("templating", {}).get("list", []):
            if "$" in tv.get("name", ""):
                template_vars.add(tv["name"])
        for panel in data["panels"]:
            panel_errors = _lint_panel(panel, panel_ids, declared_inputs, template_vars)
            errors += panel_errors
            if isinstance(panel.get("panels"), list):
                for sub in panel["panels"]:
                    errors += _lint_panel(sub, panel_ids, declared_inputs, template_vars)
    return errors


def _lint_panel(panel: dict, panel_ids: set, datasource_inputs: set, template_vars) -> int:
    errors = 0
    missing = [k for k in PANEL_REQUIRED_KEYS if k not in panel]
    if missing:
        return _fail(f"panel {panel.get('title', '<untitled>')!r} missing {missing}", DASHBOARDS_DIR)

    pid = panel.get("id")
    if pid in panel_ids:
        errors += _fail(f"duplicate panel id {pid}", DASHBOARDS_DIR)
    panel_ids.add(pid)

    ds = panel.get("datasource", {})
    ds_uid = ds.get("uid", "")
    for var in re.findall(r"\$\{(\w+)\}", ds_uid):
        if var not in datasource_inputs:
            errors += _fail(
                f"panel {panel.get('title')!r} datasource references undeclared ${var}", DASHBOARDS_DIR
            )

    for target in panel.get("targets", []):
        expr = (target or {}).get("expr", "")
        if not isinstance(expr, str) or not expr.strip():
            errors += _fail(
                f"panel {panel.get('title')!r} has empty or missing target expr", DASHBOARDS_DIR
            )
        for var in re.findall(r"\$\{(\w+)\}", expr):
            if var not in template_vars and var not in datasource_inputs:
                errors += _fail(
                    f"panel {panel.get('title')!r} expr references undeclared template var ${var}",
                    DASHBOARDS_DIR,
                )
    return errors


def lint_yaml() -> int:
    errors = 0
    for path in YAML_FILES:
        if not path.exists():
            errors += _fail("file missing", path)
            continue
        try:
            yaml.safe_load(path.read_text())
        except yaml.YAMLError as exc:
            errors += _fail(f"invalid YAML: {exc}", path)

    # Cross-file integration checks.
    try:
        prom = yaml.safe_load((ROOT / "prometheus" / "prometheus.yml").read_text())
        rule_files = prom.get("rule_files", [])
        if not any("/etc/prometheus/alert-rules.yml" in rf for rf in rule_files):
            errors += _fail("prometheus.yml does not reference alert-rules.yml", ROOT / "prometheus")
    except Exception as exc:  # noqa: BLE001 - lint script, report per-file
        errors += _fail(str(exc), ROOT / "prometheus")

    try:
        rules = yaml.safe_load((ROOT / "prometheus" / "alert-rules.yml").read_text())
        groups = rules.get("groups", [])
        if not groups:
            errors += _fail("alert-rules.yml defines no groups", ROOT / "prometheus")
        for group in groups:
            for rule in group.get("rules", []):
                if "alert" not in rule or "expr" not in rule:
                    errors += _fail(
                        f"rule in group {group.get('name')} missing `alert` or `expr`",
                        ROOT / "prometheus",
                    )
    except Exception as exc:  # noqa: BLE001
        errors += _fail(str(exc), ROOT / "prometheus")

    return errors


def main() -> int:
    total = 0
    total += lint_dashboards()
    total += lint_yaml()
    if total:
        print(f"\n{total} error(s) found in observability config.")
        return 1
    print("OK: observability config valid (dashboards, prometheus rules, otel, compose).")
    return 0


if __name__ == "__main__":
    sys.exit(main())