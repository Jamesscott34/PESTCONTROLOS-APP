const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// ✅ 1. NEW MESSAGE PUSH with reliable timestamp and high priority
exports.onMessageCreated = functions.firestore
  .document('messages/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const sender = data?.sender || '';
    const timestamp = data?.timestamp?.toDate?.() || context.timestamp?.toDate?.();
    const now = new Date();
    const ageInSeconds = timestamp ? (now - timestamp) / 1000 : 9999;

    if (!sender || ageInSeconds > 10) {
      console.log("⏩ Skipping old or missing timestamp message:", context.params.docId, "age:", ageInSeconds);
      return null;
    }

    const messageBody = `New message from ${sender}`;

    return admin.messaging().send({
      notification: {
        title: "New Message",
        body: messageBody
      },
      android: {
        priority: "high"  // ⚡ ensures fast delivery even in Doze mode
      },
      condition: `'all' in topics && !('${sender}' in topics)`
    }).then(response => {
      console.log("✅ Message notification sent:", response);
    }).catch(error => {
      console.error("❌ Error sending message notification:", error);
    });
  });


// ✅ 2. JOBWORK → Standard Field Technician Jobs
exports.onJobWorkAdded = functions.firestore
  .document('JobWork/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const tech = data?.AssignedTech || 'Technician';
    const customer = data?.CustomerName || 'Customer';
    const createdBy = data?.CreatedBy || '';
    const createdAt = data?.CreatedAt?.toDate?.();
    const now = new Date();
    const ageInSeconds = createdAt ? (now - createdAt) / 1000 : 9999;

    if (ageInSeconds > 15) {
      console.log("⏩ Skipping old JobWork entry:", context.params.docId);
      return null;
    }

    const message = `🚐 New job for ${customer} assigned to ${tech}`;

    return admin.messaging().send({
      notification: {
        title: "New Technician Job",
        body: message
      },
      condition: `'all' in topics && !('${createdBy}' in topics)`
    }).then(response => {
      console.log("✅ JobWork notification sent:", response);
    }).catch(error => {
      console.error("❌ Error sending JobWork notification:", error);
    });
  });


// ✅ 3. MANAGMENTJOBS → Higher-level, admin-initiated jobs
exports.onManagmentJobAdded = functions.firestore
  .document('ManagmentJobs/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const manager = data?.AssignedManager || 'Manager';
    const task = data?.Task || 'New Task';
    const createdBy = data?.CreatedBy || '';
    const createdAt = data?.CreatedAt?.toDate?.();
    const now = new Date();
    const ageInSeconds = createdAt ? (now - createdAt) / 1000 : 9999;

    if (ageInSeconds > 15) {
      console.log("⏩ Skipping old ManagmentJob entry:", context.params.docId);
      return null;
    }

    const message = `📋 New management task assigned to ${manager}: ${task}`;

    return admin.messaging().send({
      notification: {
        title: "New Management Job",
        body: message
      },
      condition: `'all' in topics && !('${createdBy}' in topics)`
    }).then(response => {
      console.log("✅ ManagmentJob notification sent:", response);
    }).catch(error => {
      console.error("❌ Error sending ManagmentJob notification:", error);
    });
  });
