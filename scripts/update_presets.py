"""Convert the six user supplied presets to the charging case PCM format."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

PRESETS = [
    ("steel", "钢管.MP3"),
    ("ciallo", "Ciallo～(∠・ω )⌒☆.MP3"),
    ("man", "MAN!.MP3"),
    ("what_can_i_say", "WHAT CAN I SAY.MP3"),
    ("mambo", "曼波.MP3"),
    ("far_ahead", "遥遥领先.MP3"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    target = Path(__file__).resolve().parents[1] / "app/src/main/assets/presets"
    report = []
    for identifier, name in PRESETS:
        original = args.source / name
        pcm = subprocess.check_output([
            "ffmpeg", "-v", "error", "-i", str(original), "-map", "0:a:0",
            "-ar", "16000", "-ac", "1", "-acodec", "pcm_s16le", "-f", "s16le", "pipe:1",
        ])
        if not pcm or len(pcm) % 2 or len(pcm) > 192000:
            raise ValueError(f"Preset must contain 0–6 seconds of PCM16: {name}")
        (target / f"{identifier}.pcm").write_bytes(pcm)
        report.append({"id": identifier, "source": name, "source_sha256": hashlib.sha256(original.read_bytes()).hexdigest(),
                       "pcm_bytes": len(pcm), "seconds": len(pcm) / 32000, "pcm_sha256": hashlib.sha256(pcm).hexdigest()})
    result = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report:
        args.report.write_text(result + "\n", encoding="utf-8")
    print(result)


if __name__ == "__main__":
    main()
