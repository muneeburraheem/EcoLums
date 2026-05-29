"""
FedAvg aggregation for EcoLums.

Reads per-user corrections from Firestore `federated/{userId}`,
computes a weighted average, and writes the result to `federatedGlobal/correction`.

The global correction is a two-parameter linear recalibration on top of the TFLite model:
    corrected_co2 = global_scale * model_output + global_bias

Starting values are (1.0, 0.0) — no correction. These shift as users contribute data.

Prerequisites:
    pip install firebase-admin
    export GOOGLE_APPLICATION_CREDENTIALS=path/to/serviceAccountKey.json

Usage:
    python federated_aggregation.py [--min-samples N] [--dry-run]
"""

import argparse
import datetime

import firebase_admin
from firebase_admin import credentials, firestore


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--min-samples", type=int, default=5,
                   help="Min local examples required before including a user update (default: 5)")
    p.add_argument("--dry-run", action="store_true",
                   help="Print result without writing to Firestore")
    return p.parse_args()


def fedavg(updates):
    total_n        = sum(u["sampleCount"] for u in updates)
    weighted_scale = sum(u["scale"] * u["sampleCount"] for u in updates)
    weighted_bias  = sum(u["bias"]  * u["sampleCount"] for u in updates)
    if total_n == 0:
        return 1.0, 0.0, 0
    return weighted_scale / total_n, weighted_bias / total_n, total_n


def main():
    args = parse_args()

    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    db = firestore.client()

    print("Fetching updates from Firestore `federated`...")
    docs = db.collection("federated").stream()

    updates   = []
    skipped   = 0
    total_raw = 0

    for doc in docs:
        data = doc.to_dict()
        if not data:
            continue
        total_raw += 1

        n     = data.get("sampleCount", 0)
        scale = data.get("scale", 1.0)
        bias  = data.get("bias",  0.0)

        if n < args.min_samples:
            skipped += 1
            continue
        if not (0.1 <= scale <= 10.0):
            print(f"  skipping {doc.id}: scale={scale:.4f} out of range")
            skipped += 1
            continue
        if abs(bias) > 50.0:
            print(f"  skipping {doc.id}: bias={bias:.4f} out of range")
            skipped += 1
            continue

        updates.append({"scale": scale, "bias": bias, "sampleCount": n})

    print(f"  total={total_raw}  accepted={len(updates)}  skipped={skipped}")

    if not updates:
        print("No valid updates — global correction unchanged.")
        return

    global_scale, global_bias, total_n = fedavg(updates)

    print(f"\nFedAvg ({len(updates)} participants, {total_n} samples):")
    print(f"  scale = {global_scale:.6f}")
    print(f"  bias  = {global_bias:.6f}")

    if args.dry_run:
        print("[dry-run] not writing to Firestore.")
        return

    ref = db.collection("federatedGlobal").document("correction")
    existing = ref.get()
    current_round = existing.to_dict().get("round", 0) if existing.exists else 0

    ref.set({
        "scale":        global_scale,
        "bias":         global_bias,
        "totalSamples": total_n,
        "participants": len(updates),
        "round":        current_round + 1,
        "updatedAt":    firestore.SERVER_TIMESTAMP,
    })

    print(f"Wrote round {current_round + 1} to `federatedGlobal/correction`.")

    cutoff = datetime.datetime.utcnow() - datetime.timedelta(days=30)
    stale  = db.collection("federated").where("uploadedAt", "<", cutoff).stream()
    deleted = sum(1 for doc in stale if doc.reference.delete())
    if deleted:
        print(f"Pruned {deleted} stale update(s) older than 30 days.")


if __name__ == "__main__":
    main()
