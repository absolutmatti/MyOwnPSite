const path = require("node:path");
const fs = require("node:fs");
const { test, before, after, beforeEach } = require("node:test");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");

const compatRoot = path.dirname(
  require.resolve("@firebase/rules-unit-testing/package.json")
);
const firebase = require(
  require.resolve("firebase/compat/app", {
    paths: [path.join(compatRoot, "node_modules"), "/usr/local/lib/node_modules"],
  })
);
require(
  require.resolve("firebase/compat/firestore", {
    paths: [path.join(compatRoot, "node_modules"), "/usr/local/lib/node_modules"],
  })
);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);
const serverTimestamp = () => firebase.firestore.FieldValue.serverTimestamp();

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

test("Unauthenticated user cannot read or write performers, video_links, or meta", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/performers`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/performers`).doc("p1").set({
      userId: ALICE_UID,
      name: "Test",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/video_links`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/meta`).doc("catalog").get());
});

test("Alice can create catalog meta once, and cannot overwrite or delete it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const metaRef = aliceDb.collection(`users/${ALICE_UID}/meta`).doc("catalog");

  await assertSucceeds(
    metaRef.set({
      userId: ALICE_UID,
      seeded: true,
      createdAt: serverTimestamp(),
    })
  );

  await assertFails(metaRef.update({ seeded: false }));
  await assertFails(metaRef.delete());
});

test("Alice can create, update, list, and delete her own performers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_1");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      name: "Emily Willis",
      imageUri: null,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    docRef.update({
      imageUri: "https://example.com/poster.jpg",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    docRef.update({
      imageUri: null,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/performers`).get());
  await assertSucceeds(docRef.delete());
});

test("Bob cannot read, create, update, or delete Alice's performers, video_links, or meta", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_1").set({
      userId: ALICE_UID,
      name: "Emily Willis",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/video_links`).doc("vid_1").set({
      userId: ALICE_UID,
      performerId: "perf_1",
      title: "Clip 1",
      url: "https://example.com/v1",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/performers`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/performers`).doc("perf_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/performers`).doc("perf_2").set({
      userId: ALICE_UID,
      name: "Injected",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/performers`).doc("perf_1").delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/video_links`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/video_links`).doc("vid_1").delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/meta`).doc("catalog").get());
});

test("Rejects shadow fields, oversized images, empty names, and missing parent performer on video_links", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_ghost").set({
      userId: ALICE_UID,
      name: "Test",
      isAdmin: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Empty name rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_empty").set({
      userId: ALICE_UID,
      name: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Oversized imageUri (> 250000 chars) rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_big").set({
      userId: ALICE_UID,
      name: "Big Image",
      imageUri: "x".repeat(250001),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Orphan video link rejected because parent performer doesn't exist
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/video_links`).doc("vid_orphan").set({
      userId: ALICE_UID,
      performerId: "missing_perf",
      title: "Clip 1",
      url: "https://example.com/v1",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Create performer then valid video link, update & delete video link
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/performers`).doc("perf_1").set({
      userId: ALICE_UID,
      name: "Angela White",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const vidRef = aliceDb.collection(`users/${ALICE_UID}/video_links`).doc("vid_1");
  await assertSucceeds(
    vidRef.set({
      userId: ALICE_UID,
      performerId: "perf_1",
      title: "Clip 1",
      url: "https://example.com/v1",
      thumbnailUrl: "https://example.com/thumb.jpg",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    vidRef.update({
      title: "Clip 1 Updated",
      thumbnailUrl: null,
      updatedAt: serverTimestamp(),
    })
  );

  // Immutable performerId & createdAt cannot be mutated on update
  await assertFails(
    vidRef.update({
      performerId: "perf_other",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(vidRef.delete());
});
