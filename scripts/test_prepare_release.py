import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from prepare_release import prepare, release_plan

WEB = "a" * 40
ANDROID = "b" * 40


def latest(version="1.2.8", size=1000):
    return {"tag_name": f"v{version}", "assets": [
        {"name": f"ShiftCalendarAndroid-v{version}.apk", "size": size}
    ]}


def manifest(**changes):
    return {"versionName": "1.2.8", "versionCode": 11,
            "webSourceSha": WEB, "androidSourceSha": ANDROID, **changes}


class ReleasePlanTests(unittest.TestCase):
    def test_upgrade_legacy_release_without_manifest(self):
        plan = release_plan("1.2.8", 11, latest("1.2.7"), None, WEB, ANDROID)
        self.assertEqual(plan["version_name"], "1.2.8")
        self.assertEqual(plan["version_code"], "11")

    def test_unchanged_sources_do_not_republish(self):
        self.assertEqual(release_plan("1.2.8", 11, latest(), manifest(), WEB, ANDROID),
                         {"should_build": "false"})

    def test_web_change_increments_both_versions(self):
        plan = release_plan("1.2.8", 11, latest(), manifest(), "c" * 40, ANDROID)
        self.assertEqual(plan["version_name"], "1.2.9")
        self.assertEqual(plan["version_code"], "12")

    def test_native_change_increments_both_versions(self):
        plan = release_plan("1.2.8", 11, latest(), manifest(), WEB, "c" * 40)
        self.assertEqual(plan["tag"], "v1.2.9")
        self.assertEqual(plan["apk_name"], "ShiftCalendarAndroid-v1.2.9.apk")

    def test_configured_version_and_code_never_go_backwards(self):
        plan = release_plan("2.0.0", 30, latest(), manifest(), WEB, ANDROID)
        self.assertEqual(plan["version_name"], "2.0.0")
        self.assertEqual(plan["version_code"], "30")

    def test_incomplete_release_is_replaced_with_new_version(self):
        plan = release_plan("1.2.8", 11, latest(size=0), manifest(), WEB, ANDROID)
        self.assertEqual(plan["version_name"], "1.2.9")

    def test_prepare_writes_matching_apk_and_release_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app/src/main/assets/www").mkdir(parents=True)
            (root / "app/build.gradle").write_text("versionCode 11\nversionName '1.2.8'\n", encoding="utf-8")
            with patch("prepare_release.latest_release", return_value=latest()), \
                    patch("prepare_release.release_manifest", return_value=manifest()):
                plan = prepare(root, "c" * 40, ANDROID, "example/app", None, root / "outputs")
            gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
            self.assertIn("versionCode 12", gradle)
            self.assertIn("versionName '1.2.9'", gradle)
            record = json.loads((root / "release-manifest.json").read_text(encoding="utf-8"))
            packed = json.loads((root / "app/src/main/assets/www/source-commit.json").read_text(encoding="utf-8"))
            self.assertEqual(record, packed)
            self.assertEqual(record["webSourceSha"], "c" * 40)
            self.assertEqual(record["versionName"], plan["version_name"])
            self.assertIn("should_build=true", (root / "outputs").read_text(encoding="utf-8"))

    def test_skip_keeps_sources_and_outputs_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app").mkdir()
            original = "versionCode 11\nversionName '1.2.8'\n"
            (root / "app/build.gradle").write_text(original, encoding="utf-8")
            with patch("prepare_release.latest_release", return_value=latest()), \
                    patch("prepare_release.release_manifest", return_value=manifest()):
                prepare(root, WEB, ANDROID, "example/app", None, root / "outputs")
            self.assertEqual((root / "app/build.gradle").read_text(encoding="utf-8"), original)
            self.assertFalse((root / "release-manifest.json").exists())
            self.assertEqual((root / "outputs").read_text(encoding="utf-8"), "should_build=false\n")


if __name__ == "__main__":
    unittest.main()
