# Do Not Implement Yet

The following production changes are intentionally blocked until their design/review gates are satisfied:

- deleting the prototype database and replacing it with V1;
- moving real-device `READ_SMS` ingestion into the new architecture;
- committing to public Google Play distribution assumptions;
- encrypting or deleting raw SMS without a documented retention/reparse policy;
- adding automatic retroactive rule application;
- building broad Insights UI before signed spending semantics reconcile;
- removing the old executable transaction flow before the first new vertical slice is runnable.

Fixture-based domain and UI-shell work may continue safely while these gates remain open.
