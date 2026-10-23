# Lumora: long-lived yt-dlp worker. yt-dlp is imported ONCE (the slow part of every `yt-dlp` start on a phone), then
# link-info requests are answered over stdin / stdout as one JSON object per line:
#   request : {"id": 1, "argv": ["--dump-single-json", ...], "url": "https://..."}
#   response: {"id": 1, "ok": true, "json": "<yt-dlp -J output>"}  or  {"id": 1, "ok": false, "error": "..."}
# The first line the worker prints is {"ready": true, "version": "..."} (or {"ready": false, "error": "..."}).
# argv is parsed exactly like the command line, so the result is the same as running `yt-dlp -J` once.
import json
import sys


def main():
    protocol = sys.stdout
    # anything yt-dlp prints must never corrupt the protocol channel
    sys.stdout = sys.stderr

    def emit(obj):
        protocol.write(json.dumps(obj, ensure_ascii=True, default=str) + "\n")
        protocol.flush()

    zip_path = sys.argv[1] if len(sys.argv) > 1 else ""
    if zip_path:
        sys.path.insert(0, zip_path)
    try:
        import yt_dlp
        try:
            from yt_dlp.version import __version__ as version
        except Exception:
            version = ""
    except BaseException as e:  # noqa
        emit({"ready": False, "error": "import failed: %s" % (e,)})
        return 2
    emit({"ready": True, "version": version})

    class Quiet(object):
        def debug(self, msg):
            pass

        def info(self, msg):
            pass

        def warning(self, msg):
            pass

        def error(self, msg):
            pass

    instances = {}

    def instance_for(argv):
        key = json.dumps(argv)
        ydl = instances.get(key)
        if ydl is None:
            parsed = yt_dlp.parse_options(argv)
            opts = dict(parsed.ydl_opts)
            opts["quiet"] = True
            opts["noprogress"] = True
            opts["no_warnings"] = True
            opts["logger"] = Quiet()
            ydl = yt_dlp.YoutubeDL(opts)
            if len(instances) >= 4:
                instances.pop(next(iter(instances)))
            instances[key] = ydl
        return ydl

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        rid = None
        try:
            req = json.loads(line)
            rid = req.get("id")
            if req.get("op") == "ping":
                emit({"id": rid, "ok": True, "json": "{}"})
                continue
            ydl = instance_for(list(req.get("argv") or []))
            info = ydl.extract_info(req["url"], download=False)
            if info is None:
                raise RuntimeError("no information was returned for this link")
            emit({"id": rid, "ok": True, "json": json.dumps(ydl.sanitize_info(info), ensure_ascii=True, default=str)})
        except BaseException as e:  # noqa  (SystemExit from a bad option included)
            if isinstance(e, KeyboardInterrupt):
                return 1
            msg = str(e).strip() or e.__class__.__name__
            # SystemExit = yt-dlp rejected an option (a worker problem, not a problem of the link): the caller falls back
            emit({"id": rid, "ok": False, "error": msg[:600], "infra": isinstance(e, SystemExit)})
    return 0


if __name__ == "__main__":
    sys.exit(main())
