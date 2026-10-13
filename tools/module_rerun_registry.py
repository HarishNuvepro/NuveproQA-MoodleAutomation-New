"""
Registry of regression modules for failed/skipped re-run generation.

Add a new entry when you finish automating another dashboard module — same shape as below.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class ModuleRerunSpec:
    """How to scope and re-run a module after a parallel (or serial) execution."""

    module_id: str
    label: str
    kiwi_sheet: str
    feature_files: tuple[str, ...]
    tag_prefixes: tuple[str, ...]
    aliases: tuple[str, ...] = ()
    # Extra Maven args for parallel re-run (profile -Pparallel is added by generator).
    parallel_maven_args: tuple[str, ...] = (
        "-DsuiteXmlFile=testng-parallel5.xml",
        "-Dparallel.browsers=3",
    )
    sandbox_parallel: bool = False
    defer_cleanup: bool = True
    default_module_tags: str | None = None


MODULES: dict[str, ModuleRerunSpec] = {}


def _add(spec: ModuleRerunSpec) -> None:
    MODULES[spec.module_id] = spec
    for alias in spec.aliases:
        MODULES[alias] = spec


_add(
    ModuleRerunSpec(
        module_id="users",
        label="Users",
        kiwi_sheet="Moodle_users",
        feature_files=("moodle_users.feature",),
        tag_prefixes=("U-user-", "U-USER-"),
        aliases=("moodle_users", "moodle-users"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=3"),
    )
)

_add(
    ModuleRerunSpec(
        module_id="upload_users",
        label="Upload users",
        kiwi_sheet="Upload_Users",
        feature_files=("upload_users.feature",),
        tag_prefixes=("UU-S", "UU-"),
        aliases=("upload-users", "uploadusers"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=3"),
    )
)

_add(
    ModuleRerunSpec(
        module_id="all_courses",
        label="All courses",
        kiwi_sheet="All Course Page ",
        feature_files=("all_courses.feature",),
        tag_prefixes=("ACP-S", "ACP-", "CGAP-ACP-"),
        aliases=("all-courses", "allcourses", "acp"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=3"),
    )
)

_add(
    ModuleRerunSpec(
        module_id="sandbox",
        label="Sandbox",
        kiwi_sheet="Moodle_Sandbox_Labs",
        feature_files=("sandbox.feature", "sandbox_user_functionality.feature"),
        tag_prefixes=("SB-create-", "SB-manage-", "SB-user-"),
        aliases=("sandbox_labs", "moodle_sandbox"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=5"),
        sandbox_parallel=True,
    )
)

_add(
    ModuleRerunSpec(
        module_id="difficulty_level_configuration",
        label="Difficulty level configuration",
        kiwi_sheet="Difficulty_Level_Configuration",
        feature_files=("difficulty_level_configuration.feature",),
        tag_prefixes=("DLC-",),
        aliases=("difficulty", "dlc", "difficulty-level"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=3"),
    )
)

_add(
    ModuleRerunSpec(
        module_id="cloudlabs_integration",
        label="CloudLabs integration",
        kiwi_sheet="Moodle-Cloudlabs Integration",
        feature_files=("moodle_cloudlabs_integration.feature",),
        tag_prefixes=("MCI-",),
        aliases=("cloudlabs-integration", "mci", "cloudlabs_integration"),
        parallel_maven_args=("-DsuiteXmlFile=testng-parallel5.xml", "-Dparallel.browsers=5"),
        default_module_tags="@mci",
    )
)


def resolve_module(module_key: str) -> ModuleRerunSpec | None:
    key = module_key.strip().lower().replace(" ", "_").replace("-", "_")
    if key in MODULES:
        return MODULES[key]
    for spec in {id(v): v for v in MODULES.values()}.values():
        if spec.module_id.replace("_", "") == key.replace("_", ""):
            return spec
    return None


def unique_specs() -> list[ModuleRerunSpec]:
    seen: set[str] = set()
    out: list[ModuleRerunSpec] = []
    for spec in MODULES.values():
        if spec.module_id in seen:
            continue
        seen.add(spec.module_id)
        out.append(spec)
    return sorted(out, key=lambda s: s.module_id)
