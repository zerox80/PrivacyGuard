#!/usr/bin/env python3
"""Utility to batch-rename project identifiers (names, packages, directories).

Example usage (dry-run):
    python rename_project.py \
        --root .. \
        --old-project-name PrivacyGuard \
        --new-project-name Aegilon \
        --old-package com.privacyguard.privacyguard \
        --new-package com.aegilon.aegilon

Add ``--apply`` to actually perform the changes once the dry-run output looks good.
"""
from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

DEFAULT_TEXT_EXTENSIONS: Sequence[str] = (
    ".kt",
    ".kts",
    ".java",
    ".xml",
    ".gradle",
    ".properties",
    ".pro",
    ".md",
    ".txt",
    ".json",
    ".yaml",
    ".yml",
    ".ini",
    ".cfg",
    ".bat",
    ".sh",
    ".py",
    ".html",
    ".css",
    ".js",
    ".ts",
    ".tsx",
    ".svg",
)

DEFAULT_EXCLUDED_DIRS = {".git", ".gradle", ".idea", "build", "out", ".gitlab"}


@dataclass
class Summary:
    files_updated: int = 0
    files_renamed: int = 0
    dirs_renamed: int = 0

    def log(self) -> str:
        return (
            f"files updated: {self.files_updated}, "
            f"files renamed: {self.files_renamed}, "
            f"dirs renamed: {self.dirs_renamed}"
        )


def parse_extra(value: str) -> Tuple[str, str]:
    if "=" not in value:
        raise argparse.ArgumentTypeError("Expected EXTRA value in the form 'old=new'")
    old, new = value.split("=", 1)
    return old.strip(), new.strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Rename project identifiers.")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Project root directory")
    parser.add_argument("--old-project-name", required=True, help="Current project/application name")
    parser.add_argument("--new-project-name", required=True, help="Desired project/application name")
    parser.add_argument("--old-package", required=True, help="Current root package (e.g. com.foo.bar)")
    parser.add_argument("--new-package", required=True, help="Desired root package")
    parser.add_argument(
        "--extra",
        action="append",
        type=parse_extra,
        default=[],
        help="Additional literal replacements in the form OLD=NEW (repeatable)",
    )
    parser.add_argument(
        "--text-extensions",
        nargs="*",
        default=list(DEFAULT_TEXT_EXTENSIONS),
        help="File extensions (with leading dot) to treat as text. Default covers common project files.",
    )
    parser.add_argument(
        "--exclude-dir",
        action="append",
        default=[],
        help="Directory names to skip entirely (repeatable). Defaults include .git, build, etc.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Actually apply changes. Without this flag the script performs a dry-run.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print every processed file even if unchanged.",
    )
    return parser.parse_args()


def build_replacements(args: argparse.Namespace) -> Dict[str, str]:
    pairs: List[Tuple[str, str]] = []

    def add(old: str, new: str):
        if not old or old == new:
            return
        pairs.append((old, new))

    # Project name variants
    add(args.old_project_name, args.new_project_name)
    add(args.old_project_name.lower(), args.new_project_name.lower())
    add(args.old_project_name.upper(), args.new_project_name.upper())
    add(args.old_project_name.title(), args.new_project_name.title())

    # Package replacements
    add(args.old_package, args.new_package)
    add(args.old_package.lower(), args.new_package.lower())
    old_pkg_path = args.old_package.replace(".", os.sep)
    new_pkg_path = args.new_package.replace(".", os.sep)
    add(old_pkg_path, new_pkg_path)

    # Gradle-friendly package strings (with slashes)
    add(args.old_package.replace(".", "/"), args.new_package.replace(".", "/"))

    # Additional user-provided pairs
    pairs.extend(args.extra)

    # Preserve insertion order
    seen: Dict[str, str] = {}
    for old, new in pairs:
        if old not in seen:
            seen[old] = new
    return seen


def collect_paths(root: Path, exclude_dirs: Iterable[str]) -> Tuple[List[Path], List[Path]]:
    excluded = set(exclude_dirs)
    directories: List[Path] = []
    files: List[Path] = []

    for dirpath, dirnames, filenames in os.walk(root):
        current = Path(dirpath)
        dirnames[:] = [d for d in dirnames if d not in excluded]
        for dirname in dirnames:
            directories.append(current / dirname)
        for filename in filenames:
            files.append(current / filename)
    return directories, files


def rename_path(entity: Path, replacements: Dict[str, str], dry_run: bool, summary: Summary) -> None:
    original_name = entity.name
    new_name = original_name
    for old, new in replacements.items():
        if old in new_name:
            new_name = new_name.replace(old, new)
    if new_name == original_name:
        return

    target = entity.with_name(new_name)
    if dry_run:
        print(f"[DRY-RUN] rename {entity} -> {target}")
    else:
        if target.exists():
            if entity.is_dir() and target.is_dir():
                for child in entity.iterdir():
                    child_target = target / child.name
                    if child_target.exists():
                        raise FileExistsError(
                            f"Cannot merge {child} into existing {child_target}. Rename manually first."
                        )
                    child.rename(child_target)
                entity.rmdir()
            else:
                raise FileExistsError(f"Target already exists: {target}")
        else:
            entity.rename(target)
    if entity.is_dir():
        summary.dirs_renamed += 1
    else:
        summary.files_renamed += 1


def try_read_text(path: Path) -> Tuple[str | None, str | None]:
    for encoding in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return path.read_text(encoding=encoding), encoding
        except UnicodeDecodeError:
            continue
    return None, None


def update_file_contents(
    path: Path,
    replacements: Dict[str, str],
    dry_run: bool,
    summary: Summary,
    verbose: bool,
) -> None:
    text, encoding = try_read_text(path)
    if text is None:
        if verbose:
            print(f"[SKIP] non-text or unsupported encoding: {path}")
        return

    updated = text
    for old, new in replacements.items():
        updated = updated.replace(old, new)

    if updated == text:
        if verbose:
            print(f"[UNCHANGED] {path}")
        return

    if dry_run:
        print(f"[DRY-RUN] update {path} (encoding {encoding})")
    else:
        path.write_text(updated, encoding=encoding)
    summary.files_updated += 1


def main() -> None:
    args = parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise SystemExit(f"Root directory not found: {root}")

    replacements = build_replacements(args)
    exclude_dirs = set(DEFAULT_EXCLUDED_DIRS)
    exclude_dirs.update(args.exclude_dir)

    summary = Summary()
    dry_run = not args.apply

    print(f"Scanning under {root}")
    directories, files = collect_paths(root, exclude_dirs)

    # Rename directories first (deepest path first)
    for directory in sorted(
        directories, key=lambda p: len(p.relative_to(root).parts), reverse=True
    ):
        rename_path(directory, replacements, dry_run, summary)

    # After renaming directories, gather files again to ensure paths are fresh
    _, files = collect_paths(root, exclude_dirs)

    # Rename files (by filename)
    for file_path in sorted(files, key=lambda p: len(p.relative_to(root).parts), reverse=True):
        rename_path(file_path, replacements, dry_run, summary)

    # Update file contents
    text_exts = {ext.lower() for ext in args.text_extensions}
    for file_path in files:
        if file_path.suffix.lower() in text_exts or file_path.suffix == "":
            update_file_contents(file_path, replacements, dry_run, summary, args.verbose)
        elif args.verbose:
            print(f"[SKIP] binary extension: {file_path}")

    mode = "DRY-RUN" if dry_run else "APPLY"
    print(f"Done ({mode}). Summary: {summary.log()}")
    if dry_run:
        print("No changes were written. Re-run with --apply once the output looks correct.")


if __name__ == "__main__":
    main()
