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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validInspectionPayload(uid, createdDate, updatedDate) {
  return {
    userId: uid,
    roadName: "Jl. Angkut Afdeling III",
    segmentLocation: "KM 04+200 - KM 05+800",
    regionName: "Pekanbaru, Riau",
    trafficIntensity: "TINGGI",
    videoSourceLabel: "CCTV_Kebun_01.mp4 (18 dtk)",
    framesAnalyzed: 5,
    roadSummary: "Terdeteksi retak buaya dan dua lubang sedang di lajur kiri.",
    pciScore: 62,
    iriEstimate: 3.8,
    conditionCategory: "SEDANG",
    optimalMaintenanceMonth: "Mei 2027",
    urgencyWindow: "Menunggu Jendela Kering",
    preventiveMethod: "Penambalan lubang (patching) dan perataan grader.",
    maintenanceRationale: "Curah hujan tinggi Nov-Jan; Mei memasuki awal musim kemarau sebelum PCI turun di bawah 50.",
    rainfallSummary: "Curah hujan tinggi Nov-Jan (260-310 mm/bln), menurun mulai Mei (120 mm/bln).",
    rainfallSourceNote: "Google Search Grounding: Pola Curah Hujan Riau",
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

test("Unauthenticated user: cannot read or write inspections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("inspections").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1")
      .set(validInspectionPayload(ALICE_UID, now, now))
  );
});

test("Authenticated owner: valid create, list, partial schedule update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1")
      .set(validInspectionPayload(ALICE_UID, created, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1").update({
      maintenanceStatus: "PROSES",
      optimalMaintenanceMonth: "Desember 2026",
      maintenanceNotes: "Tim survei lapangan sudah dijadwalkan.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_1").delete()
  );
});

test("Security bounds: reject cross-user access, ghost fields, immutable mutation, invalid status, future timestamp", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore()
      .collection("users").doc(BOB_UID).collection("inspections").doc("bob_insp")
      .set(validInspectionPayload(BOB_UID, created, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cross-user read/write/delete denied
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("inspections").doc("bob_insp").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("inspections").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("inspections").doc("bob_insp").update({
      maintenanceStatus: "SELESAI",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost @DocumentId `id` field rejected
  const withId = validInspectionPayload(ALICE_UID, created, created);
  withId.id = "insp_2";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_2").set(withId)
  );

  // Create valid doc for update negative tests
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_3")
      .set(validInspectionPayload(ALICE_UID, created, created))
  );

  // Reject mutating immutable createdAt or pciScore
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_3").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_3").update({
      pciScore: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid maintenanceStatus enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_3").update({
      maintenanceStatus: "INVALID_STATUS",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("inspections").doc("insp_4")
      .set(validInspectionPayload(ALICE_UID, new Date(Date.now() + 60000), new Date(Date.now() + 60000)))
  );
});
