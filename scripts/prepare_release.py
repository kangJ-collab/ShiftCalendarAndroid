"""Choose an immutable APK release for the current web and Android sources."""
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request


def version_parts(value):
    match = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)", str(value))
    if not match:
        raise ValueError(f"Unsupported release version: {value}")
    return tuple(map(int, match.groups()))


def release_plan(configured_version, configured_code, latest, manifest,
                 web_sha, android_sha):
    configured = version_parts(configured_version)
    previous = version_parts(latest["tag_name"]) if latest else None
    expected_apk = f"ShiftCalendarAndroid-v{'.'.join(map(str, previous))}.apk" if previous else ""
    complete = any(asset.get("name") == expected_apk and asset.get("size", 0) > 0
                   for asset in (latest or {}).get("assets", []))
    same_sources = bool(manifest) and (
        manifest.get("webSourceSha") == web_sha and
        manifest.get("androidSourceSha") == android_sha
    )
    if (same_sources and complete and previous >= configured and
            manifest.get("versionName") == ".".join(map(str, previous))):
        return {"should_build": "false"}

    next_version = configured
    if previous and next_version <= previous:
        next_version = (previous[0], previous[1], previous[2] + 1)
    code = max(int(configured_code), int((manifest or {}).get("versionCode", 0)) + 1)
    if not 0 < code <= 2_100_000_000:
        raise ValueError("Android versionCode is outside the supported range")
    name = ".".join(map(str, next_version))
    return {
        "should_build": "true", "version_name": name, "version_code": str(code),
        "tag": f"v{name}", "apk_name": f"ShiftCalendarAndroid-v{name}.apk",
    }


def read_json(url, token=None):
    headers = {"User-Agent": "ShiftCalendarAndroid-release",
               "Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def latest_release(repository, token):
    try:
        return read_json(f"https://api.github.com/repos/{repository}/releases/latest", token)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def release_manifest(repository, release):
    for asset in (release or {}).get("assets", []):
        if asset.get("name") != "release-manifest.json":
            continue
        url = asset.get("browser_download_url", "")
        if not url.startswith(f"https://github.com/{repository}/releases/download/"):
            raise ValueError("Unexpected release manifest URL")
        # Release assets are public; never forward an API token to download hosts.
        return read_json(url)
    return None


def prepare(root, web_sha, android_sha, repository, token, output):
    if not re.fullmatch(r"[0-9a-f]{40}", web_sha) or not re.fullmatch(r"[0-9a-f]{40}", android_sha):
        raise ValueError("Both source commit IDs are required")
    gradle_path = root / "app/build.gradle"
    gradle = gradle_path.read_text(encoding="utf-8")
    configured_version = re.search(r"versionName\s+'([^']+)'", gradle).group(1)
    configured_code = int(re.search(r"versionCode\s+(\d+)", gradle).group(1))
    latest = latest_release(repository, token)
    manifest = release_manifest(repository, latest)
    plan = release_plan(configured_version, configured_code, latest, manifest, web_sha, android_sha)
    with output.open("a", encoding="utf-8") as stream:
        for key, value in plan.items():
            stream.write(f"{key}={value}\n")
    if plan["should_build"] == "false":
        print("Published APK already contains these web and Android sources; no rebuild needed.")
        return plan

    gradle = re.sub(r"versionName\s+'[^']+'", f"versionName '{plan['version_name']}'", gradle)
    gradle = re.sub(r"versionCode\s+\d+", f"versionCode {plan['version_code']}", gradle)
    gradle_path.write_text(gradle, encoding="utf-8")
    record = {
        "schemaVersion": 1, "versionName": plan["version_name"],
        "versionCode": int(plan["version_code"]),
        "webSourceSha": web_sha, "androidSourceSha": android_sha,
    }
    content = json.dumps(record, indent=2) + "\n"
    (root / "release-manifest.json").write_text(content, encoding="utf-8")
    # A visible filename survives Android's default asset packaging filters.
    (root / "app/src/main/assets/www/source-commit.json").write_text(content, encoding="utf-8")
    print(f"Preparing {plan['tag']} (versionCode {plan['version_code']})")
    return plan


if __name__ == "__main__":
    prepare(Path.cwd(), os.environ["WEB_SOURCE_SHA"], os.environ["GITHUB_SHA"],
            os.environ["GITHUB_REPOSITORY"], os.environ.get("GH_TOKEN"),
            Path(os.environ["GITHUB_OUTPUT"]))
