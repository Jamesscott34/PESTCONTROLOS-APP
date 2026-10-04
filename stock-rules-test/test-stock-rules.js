/**
 * Stock security-rule checks against the local Firestore emulator.
 * The emulator loads firestore.rules itself. This script does not deploy rules.
 *
 * From the repo root:
 * npx firebase emulators:exec --only firestore --project demo-stock-rules "node stock-rules-test/test-stock-rules.js"
 */
const host = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const projectId = "demo-stock-rules";

function jwt(uid) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none", typ: "JWT" })}.${encode({
    sub: uid,
    user_id: uid,
    aud: projectId,
    iss: `https://securetoken.google.com/${projectId}`,
  })}.`;
}

function fieldsFromObject(value) {
  const fields = {};
  Object.keys(value).forEach((key) => {
    fields[key] = toValue(value[key]);
  });
  return { fields };
}

function toValue(value) {
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toValue) } };
  if (value && typeof value === "object") return { mapValue: fieldsFromObject(value) };
  if (typeof value === "string") return { stringValue: value };
  if (Number.isInteger(value)) return { integerValue: String(value) };
  throw new Error("Unsupported field value");
}

function fromFields(fields) {
  const out = {};
  Object.keys(fields || {}).forEach((key) => {
    out[key] = fromValue(fields[key]);
  });
  return out;
}

function fromValue(value) {
  if (!value) return null;
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.integerValue !== undefined) return Number(value.integerValue);
  if (value.timestampValue !== undefined) return value.timestampValue;
  if (value.mapValue) return fromFields(value.mapValue.fields);
  if (value.arrayValue) return (value.arrayValue.values || []).map(fromValue);
  return null;
}

async function call(method, path, token, body) {
  const response = await fetch(`http://${host}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch (ignored) {
      json = null;
    }
  }
  return { status: response.status, json, text };
}

async function clearAll() {
  const response = await fetch(
    `http://${host}/emulator/v1/projects/${projectId}/databases/(default)/documents`,
    { method: "DELETE" }
  );
  if (!response.ok) throw new Error(await response.text());
}

async function putDoc(path, data) {
  const result = await call("PATCH", basePath(path), "owner", fieldsFromObject(data));
  if (result.status >= 400) {
    throw new Error(`Seed ${path} failed ${result.status} ${result.text}`);
  }
}

function basePath(path) {
  return `/v1/projects/${projectId}/databases/(default)/documents/${path}`;
}

function requestDoc(id, uid, name, status) {
  const doc = {
    requestId: id,
    requesterUid: uid,
    requesterName: name,
    weekKey: "2026-W40",
    status,
    createdAt: new Date().toISOString(),
    items: [{ productName: "Ruby Blocks", quantity: 2, productType: "rodenticide" }],
  };
  if (status === "ready" || status === "retrieved") {
    doc.approvedAt = new Date().toISOString();
    doc.approvedByUid = "admin1";
    doc.approvedByName = "Ada Admin";
  }
  if (status === "retrieved") {
    doc.retrievedAt = "2026-10-03T12:00:00Z";
    doc.retrievedByUid = "admin1";
    doc.retrievedByName = "Ada Admin";
  }
  return doc;
}

async function expectDenied(label, token, path) {
  const result = await call("DELETE", basePath(path), token);
  if (result.status !== 403 && result.status !== 400) {
    throw new Error(`${label} expected deny, got ${result.status} ${result.text}`);
  }
}

async function expectDeleted(label, token, path) {
  const result = await call("DELETE", basePath(path), token);
  if (result.status !== 200) {
    throw new Error(`${label} expected delete, got ${result.status} ${result.text}`);
  }
}

async function readDoc(token, path) {
  const result = await call("GET", basePath(path), token);
  if (result.status === 404) return null;
  if (result.status !== 200) throw new Error(`Read ${path} failed ${result.status} ${result.text}`);
  return fromFields(result.json.fields);
}

async function main() {
  await clearAll();
  await putDoc("users/admin1", { role: "admin", name: "Ada Admin" });
  await putDoc("users/super1", { role: "super_admin", name: "Sam Super" });
  await putDoc("users/tech1", { role: "tech", name: "James Scott" });
  await putDoc("users/tech2", { role: "tech", name: "John Murphy" });
  await putDoc("stock_requests/pending1", requestDoc("pending1", "tech1", "James Scott", "pending"));
  await putDoc("stock_requests/ready1", requestDoc("ready1", "tech1", "James Scott", "ready"));
  await putDoc("stock_requests/retrieved1", requestDoc("retrieved1", "tech1", "James Scott", "retrieved"));
  await putDoc("stock_requests/other1", requestDoc("other1", "tech2", "John Murphy", "retrieved"));
  await putDoc("stock_requests/superDelete1", requestDoc("superDelete1", "tech1", "James Scott", "retrieved"));
  await putDoc("stock_weekly_checks/tech1_2026-W40", {
    userUid: "tech1",
    userName: "James Scott",
    weekKey: "2026-W40",
    response: "requested",
    createdAt: new Date().toISOString(),
    requestId: "retrieved1",
  });

  const tech = jwt("tech1");
  const admin = jwt("admin1");
  const superAdmin = jwt("super1");

  await expectDenied("technician retrieved", tech, "stock_requests/retrieved1");
  await expectDenied("technician pending", tech, "stock_requests/pending1");
  await expectDenied("admin pending", admin, "stock_requests/pending1");
  await expectDenied("admin ready", admin, "stock_requests/ready1");
  await expectDeleted("admin retrieved", admin, "stock_requests/retrieved1");
  await expectDeleted("super_admin retrieved", superAdmin, "stock_requests/superDelete1");

  if (await readDoc("owner", "stock_requests/retrieved1")) {
    throw new Error("Deleted request is still present");
  }
  const weekly = await readDoc("owner", "stock_weekly_checks/tech1_2026-W40");
  if (!weekly) throw new Error("Weekly check was removed");
  if (weekly.response !== "requested" || weekly.requestId !== "retrieved1") {
    throw new Error("Weekly check was changed");
  }
  const other = await readDoc("owner", "stock_requests/other1");
  if (!other || other.requesterName !== "John Murphy" || other.status !== "retrieved") {
    throw new Error("Another stock request was affected");
  }
  const pending = await readDoc("owner", "stock_requests/pending1");
  const ready = await readDoc("owner", "stock_requests/ready1");
  if (!pending || pending.status !== "pending") throw new Error("Pending request changed");
  if (!ready || ready.status !== "ready") throw new Error("Ready request changed");

  console.log("Stock rules tests passed");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
