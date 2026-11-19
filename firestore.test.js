const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ESTATE_ID = "kebun_sei_rokan";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validInspectionPayload(uid, createdDate, updatedDate, overrides = {}) {
  return {
    userId: uid,
    estateId: ESTATE_ID,
    inspectorName: "Mandor Lapangan",
    roadName: "Jalan Angkut TBS Blok I4",
    segmentLocation: "Kebun Sei Rokan • Afdeling I • Blok I4",
    regionName: "Kebun Sei Rokan, Rokan Hulu, Riau",
    trafficIntensity: "TINGGI",
    videoSourceLabel: "Kamera Live (10 dtk)",
    framesAnalyzed: 5,
    roadSummary: "Terdeteksi retak alur dan dua lubang sedang di badan jalan angkut TBS.",
    pciScore: 62,
    iriEstimate: 3.8,
    conditionCategory: "SEDANG",
    optimalMaintenanceMonth: "Mei 2027",
    urgencyWindow: "Menunggu Jendela Kering",
    preventiveMethod: "Penambalan lubang (patching) dan perataan grader.",
    maintenanceRationale: "Curah hujan tinggi Nov-Jan; Mei memasuki awal musim kemarau sebelum PCI turun di bawah 50.",
    rainfallSummary: "Curah hujan tinggi Nov-Jan (260-310 mm/bln), menurun mulai Mei (120 mm/bln).",
    rainfallSourceNote: "Google Search Grounding: Pola Curah Hujan Rokan Hulu Riau",
    costSavingsPercent: 48,
    maintenanceStatus: "TERJADWAL",
    maintenanceNotes: "",
    damages: [
      {
        type: "Lubang (Pothole)",
        severity: "SEDANG",
        affectedPercent: 12,
        frameIndex: 2,
        description: "Lubang berdiameter ~35 cm di tepi lajur kiri.",
        recommendedAction: "Cold-mix patching segera sebelum hujan lebat."
      }
    ],
    forecast: [
      { monthOffset: 0, monthLabel: "Nov 26", predictedPci: 62, rainfallMm: 270, riskLevel: "WASPADA" },
      { monthOffset: 1, monthLabel: "Des 26", predictedPci: 58, rainfallMm: 295, riskLevel: "WASPADA" },
      { monthOffset: 2, monthLabel: "Jan 27", predictedPci: 53, rainfallMm: 240, riskLevel: "WASPADA" },
      { monthOffset: 3, monthLabel: "Feb 27", predictedPci: 46, rainfallMm: 185, riskLevel: "KRITIS" }
    ],
    createdAt: createdDate,
    updatedAt: updatedDate,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write shared inspections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb.collection("inspections").where("estateId", "==", ESTATE_ID).get()
  );
  await assertFails(
    unauthDb.collection("inspections").doc("insp_1")
      .set(validInspectionPayload(ALICE_UID, now, now))
  );
});

test("Shared visibility: Alice creates inspection, Bob sees it via estateId query and can update schedule; only Alice can delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Empty filtered list succeeds
  await assertSucceeds(
    bobDb.collection("inspections").where("estateId", "==", ESTATE_ID).get()
  );

  // Alice creates shared inspection
  await assertSucceeds(
    aliceDb.collection("inspections").doc("insp_1")
      .set(validInspectionPayload(ALICE_UID, created, created))
  );

  // Bob can read and list shared Kebun Sei Rokan inspections
  await assertSucceeds(bobDb.collection("inspections").doc("insp_1").get());
  await assertSucceeds(
    bobDb.collection("inspections").where("estateId", "==", ESTATE_ID).get()
  );

  // Unfiltered list without estateId must fail
  await assertFails(bobDb.collection("inspections").get());

  // Bob (another team member) can update maintenance schedule fields
  await assertSucceeds(
    bobDb.collection("inspections").doc("insp_1").update({
      maintenanceStatus: "PROSES",
      optimalMaintenanceMonth: "Desember 2026",
      maintenanceNotes: "Tim grader sudah menuju Blok I4.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot delete Alice's inspection
  await assertFails(bobDb.collection("inspections").doc("insp_1").delete());

  // Alice (creator) can delete her inspection
  await assertSucceeds(aliceDb.collection("inspections").doc("insp_1").delete());
});

test("Security bounds: reject spoofing userId, wrong estateId, ghost fields, immutable mutation, invalid status, future timestamp", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject userId spoofing
  await assertFails(
    aliceDb.collection("inspections").doc("spoof_1")
      .set(validInspectionPayload(BOB_UID, created, created))
  );

  // Reject wrong estateId
  await assertFails(
    aliceDb.collection("inspections").doc("wrong_estate")
      .set(validInspectionPayload(ALICE_UID, created, created, { estateId: "other_estate" }))
  );

  // Reject ghost @DocumentId `id` field
  await assertFails(
    aliceDb.collection("inspections").doc("ghost_id")
      .set(validInspectionPayload(ALICE_UID, created, created, { id: "ghost_id" }))
  );

  // Create valid doc for update negative tests
  await assertSucceeds(
    aliceDb.collection("inspections").doc("insp_3")
      .set(validInspectionPayload(ALICE_UID, created, created))
  );

  // Reject mutating immutable createdAt, userId, estateId, or pciScore
  await assertFails(
    aliceDb.collection("inspections").doc("insp_3").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("inspections").doc("insp_3").update({
      pciScore: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid maintenanceStatus enum
  await assertFails(
    aliceDb.collection("inspections").doc("insp_3").update({
      maintenanceStatus: "INVALID_STATUS",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("inspections").doc("insp_4")
      .set(validInspectionPayload(ALICE_UID, new Date(Date.now() + 60000), new Date(Date.now() + 60000)))
  );
});
