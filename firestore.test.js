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

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: Alice cannot read Bob's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      role: "VIEWER",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Authenticated user: Alice can read and write her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      role: "SUPER_ADMIN",
      displayName: "Alice Quant",
      riskTolerance: "INSTITUTIONAL",
      alertSoundEnabled: true,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: Alice can create price alert in her subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alert_1").set({
      alertId: "alert_1",
      userId: ALICE_UID,
      targetPrice: 2875.50,
      condition: "ABOVE",
      type: "SPOT",
      note: "London High breakout alert",
      triggered: false,
      createdAt: Date.now(),
    })
  );
});

test("Authenticated user: Bob cannot read Alice's price alerts", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("price_alerts").doc("alert_1").set({
      alertId: "alert_1",
      userId: ALICE_UID,
      targetPrice: 2875.50,
      condition: "ABOVE",
      type: "SPOT",
      triggered: false,
      createdAt: Date.now(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alert_1").get());
});

test("Authenticated user: Alice can manage simulated trades", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_1").set({
      tradeId: "trade_1",
      userId: ALICE_UID,
      timeframe: "15m",
      direction: "BUY",
      lots: 1.0,
      entryPrice: 2860.00,
      stopLoss: 2854.00,
      takeProfit1: 2875.00,
      takeProfit2: 2886.00,
      status: "OPEN",
      pnlDollar: 0.0,
      pnlPips: 0.0,
      signalReason: "Consensus bull breakout",
      entryTime: Date.now(),
    })
  );
});
