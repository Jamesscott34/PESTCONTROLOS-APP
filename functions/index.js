const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

/** Admin = user document 001 in users/staff collection (James). No hardcoded email. */
const ADMIN_USER_ID = '001';
const USER_COLLECTIONS = ['Staff', 'staff', 'Users', 'users'];
/** Firestore doc where OpenRouter API key is stored (AI-Chat/AI-API, field KEY). Apps read it; only James via updateOpenRouterKey can write. */
const AI_CHAT_KEY_PATH = 'AI-Chat/AI-API';

/** Returns the email of user 001 from Firestore, or null if not found. */
async function getAdminEmail() {
  const db = admin.firestore();
  for (const coll of USER_COLLECTIONS) {
    try {
      const doc = await db.collection(coll).doc(ADMIN_USER_ID).get();
      if (doc && doc.exists) {
        const data = doc.data();
        const email = (data && (data.email || data.Email));
        if (email && String(email).trim()) return String(email).trim();
      }
    } catch (e) {
      continue;
    }
  }
  return null;
}

/** Save notification to Firestore for in-app notification history */
async function saveNotificationHistory(recipientTopic, title, body, type, data) {
  try {
    await admin.firestore()
      .collection('notifications')
      .doc(recipientTopic.toLowerCase())
      .collection('items')
      .add({
        title: title || 'Notification',
        body: body || '',
        type: type || 'general',
        data: data || {},
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        read: false
      });
  } catch (e) {
    console.warn('Could not save notification history:', e.message);
  }
}

// ✅ 1a. CONVERSATION MESSAGES → Push only to recipients (1:1 = other person only, group = everyone except sender)
exports.onConversationMessageCreated = functions.firestore
  .document('conversations/{convId}/messages/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const sender = (data?.sender || 'Unknown').trim();
    const body = data?.body || '';
    const convId = context.params.convId;
    const timestamp = data?.createdAt?.toDate?.() || data?.timestamp?.toDate?.();
    const now = new Date();
    const ageInSeconds = timestamp ? (now - timestamp) / 1000 : 9999;

    if (!sender || ageInSeconds > 15) return null;

    const isUrgent = data?.isUrgent === true;
    const urgentPrefix = isUrgent ? '🔴 ' : '';
    const notificationTitle = `${urgentPrefix}💬 ${sender}`;
    const notificationBody = body.length > 80 ? body.substring(0, 80) + '...' : body;

    const participants = getConversationParticipants(convId);
    const senderLower = sender.toLowerCase();
    const recipients = participants.filter(p => p.toLowerCase() !== senderLower);

    if (recipients.length === 0) return null;

    const payload = {
      notification: { title: notificationTitle, body: notificationBody },
      data: {
        sender, convId, messageId: context.params.docId,
        type: 'conversation_message'
      },
      android: {
        priority: "high",
        notification: {
          channelId: "messages",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
          icon: "ic_notification"
        }
      }
    };

    try {
      // In-app only notifications: save to in-app notification history, do not send push.
      for (const recipient of recipients) {
        await saveNotificationHistory(recipient.toLowerCase(), notificationTitle, notificationBody,
          'conversation_message', payload.data);
      }
      console.log("✅ Conversation in-app notification saved:", convId, sender, "->", recipients);
      return null;
    } catch (error) {
      console.error("❌ Conversation message push error:", error);
      throw error;
    }
  });

function getConversationParticipants(convId) {
  if (convId === 'group') {
    return ['james', 'ian', 'kristine', 'dean'];
  }
  const parts = (convId || '').split('_');
  return parts.length >= 2 ? [parts[0].toLowerCase(), parts[1].toLowerCase()] : [];
}

// ✅ 1b. LEGACY: Top-level messages (kept for backward compat)
exports.onMessageCreated = functions.firestore
  .document('messages/{docId}')
  .onCreate(async (snap, context) => {
    // Disabled: legacy collection used `'all' in topics` broadcasts.
    // We now send chat notifications via `onConversationMessageCreated` to recipients only.
    return null;
  });

// ✅ 2. JOBWORK (in-app only)
// Rules:
// - If Ian or Kristine adds/assigns a job: assigned technician is notified (not the sender)
// - If James or Dean adds a job: Ian and Kristine are notified
exports.onJobWorkAdded = functions.firestore
  .document('JobWork/{docId}')
  .onCreate(async (snap, context) => {
    // Disabled: JobWork in-app notifications are now written by the Android client
    // (so they work without Cloud Functions, and to avoid duplicate notification records).
    return null;

    const data = snap.data();
    const tech = data?.AssignedTech || 'Technician';
    const customer = data?.CustomerName || 'Customer';
    const jobType = data?.JobType || 'Service';
    const createdBy = (data?.CreatedBy || '').trim();
    const createdAt = data?.CreatedAt?.toDate?.();
    const now = new Date();
    const ageInSeconds = createdAt ? (now - createdAt) / 1000 : 9999;

    if (ageInSeconds > 15) {
      console.log("⏩ Skipping old JobWork entry:", context.params.docId);
      return null;
    }

    const baseMessage = {
      data: {
        jobId: context.params.docId,
        assignedTech: tech,
        customerName: customer,
        jobType: jobType,
        createdBy: createdBy,
        type: 'jobwork'
      },
      android: {
        priority: "high",
        notification: {
          channelId: "jobs",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
          icon: "ic_notification"
        }
      }
    };

    try {
      const createdLower = createdBy.toLowerCase();
      const techLower = (tech || '').toLowerCase();

      if (createdLower === 'ian' || createdLower === 'kristine') {
        // Ian/Kristine assigns: notify assigned technician (not the sender)
        if (!techLower || techLower === createdLower) return null;
        await saveNotificationHistory(tech.toLowerCase(), '🚐 New Job Assignment',
          `${jobType} job for ${customer} assigned to you`, 'jobwork', baseMessage.data);
        console.log("✅ JobWork in-app notification saved for", tech, "(", createdBy, "added)");
        return null;
      } else if (createdLower === 'james' || createdLower === 'dean') {
        // James/Dean adds: notify Ian and Kristine, AND the assigned technician
        const nTitle = '🚐 New Job Added';
        const nBody = `${createdBy} added a ${jobType} job for ${customer} (assigned to ${tech})`;
        await saveNotificationHistory('ian', nTitle, nBody, 'jobwork', baseMessage.data);
        await saveNotificationHistory('kristine', nTitle, nBody, 'jobwork', baseMessage.data);
        if (techLower && techLower !== createdLower) {
          await saveNotificationHistory(techLower, '🚐 New Job Assignment',
            `${jobType} job for ${customer} assigned to you`, 'jobwork', baseMessage.data);
          console.log("✅ JobWork in-app notification saved for Ian + Kristine +", tech, "(", createdBy, "added)");
        } else {
          console.log("✅ JobWork in-app notification saved for Ian + Kristine (", createdBy, "added)");
        }
        return null;
      }
      return null;
    } catch (error) {
      console.error("❌ Error sending JobWork notification:", error);
      throw error;
    }
  });

// ✅ 3. ENHANCED MANAGMENTJOBS → Higher-level, admin-initiated jobs
exports.onManagmentJobAdded = functions.firestore
  .document('ManagmentJobs/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const manager = data?.AssignedManager || 'Manager';
    const task = data?.Task || 'New Task';
    const priority = data?.Priority || 'Normal';
    const createdBy = data?.CreatedBy || '';
    const createdAt = data?.CreatedAt?.toDate?.();
    const now = new Date();
    const ageInSeconds = createdAt ? (now - createdAt) / 1000 : 9999;

    if (ageInSeconds > 15) {
      console.log("⏩ Skipping old ManagmentJob entry:", context.params.docId);
      return null;
    }

    const priorityEmoji = priority === 'High' ? '🔴' : priority === 'Medium' ? '🟡' : '🟢';
    const notificationTitle = `${priorityEmoji} New Management Task`;
    const notificationBody = `Task assigned to ${manager}: ${task}`;

    try {
      // In-app only notifications
      if (!manager || manager.toLowerCase() === createdBy.toLowerCase()) return null;
      await saveNotificationHistory(manager.toLowerCase(), notificationTitle, notificationBody,
        'management', {
          jobId: context.params.docId,
          assignedManager: manager,
          task: task,
          priority: priority,
          createdBy: createdBy,
          type: 'management'
        });
      console.log("✅ ManagmentJob in-app notification saved:", {
        jobId: context.params.docId,
        manager: manager,
        task: task,
        priority: priority
      });
      return null;
    } catch (error) {
      console.error("❌ Error sending ManagmentJob notification:", {
        error: error.message,
        jobId: context.params.docId
      });
      throw error;
    }
  });

// ✅ 4. CONTRACT UPDATES → Notify when contracts are modified (explicit triggers for collections with spaces)
function createContractUpdateFunction(collectionName) {
  return functions.firestore
    .document(collectionName + '/{docId}')
    .onUpdate(async (change, context) => {
      const beforeData = change.before.data();
      const afterData = change.after.data();

      if (beforeData?.lastVisit !== afterData?.lastVisit) {
        const contractName = afterData?.name || 'Contract';
        const lastVisit = afterData?.lastVisit || 'N/A';

        try {
          // In-app only notifications
          const recipient = collectionName.replace(' Contracts', '').toLowerCase();
          await saveNotificationHistory(recipient, '📅 Contract Updated',
            `${contractName} - Last visit: ${lastVisit}`, 'contract_update', {
              contractId: context.params.docId,
              contractName: contractName,
              lastVisit: lastVisit,
              userName: collectionName.replace(' Contracts', ''),
              type: 'contract_update'
            });
          console.log("✅ Contract update in-app notification saved:", context.params.docId);
          return null;
        } catch (error) {
          console.error("❌ Error sending contract update notification:", error);
          throw error;
        }
      }
      return null;
    });
}

exports.onJamesContractUpdated = createContractUpdateFunction('James Contracts');
exports.onIanContractUpdated = createContractUpdateFunction('Ian Contracts');
exports.onDeanContractUpdated = createContractUpdateFunction('Dean Contracts');
exports.onKristineContractUpdated = createContractUpdateFunction('Kristine Contracts');

// ✅ 4b. CONTRACT CREATED (in-app only)
// Rules:
// - If Ian or Kristine adds a contract to another tech's list: that tech is notified
// - If James adds to James' own list: Ian and Kristine are notified
// - If Dean adds to Dean's own list: Ian and Kristine are notified
function createContractCreatedFunction(collectionName) {
  return functions.firestore
    .document(collectionName + '/{docId}')
    .onCreate(async (snap, context) => {
      // Disabled: Contract assignment in-app notifications are now written by the Android client
      // (so they work without Cloud Functions, and to avoid duplicate notification records).
      return null;

      const data = snap.data();
      const createdBy = (data?.createdBy || '').trim();
      const contractName = data?.name || 'Contract';
      const assignedTech = collectionName.replace(' Contracts', '');

      try {
        const createdLower = createdBy.toLowerCase();
        const ownerLower = assignedTech.toLowerCase();

        // Own-list adds: James/Dean -> notify Ian + Kristine
        if ((createdLower === 'james' && ownerLower === 'james') || (createdLower === 'dean' && ownerLower === 'dean')) {
          const msg = {
            notification: {
              title: '📋 New Contract Added',
              body: `${createdBy} added ${contractName} to their contracts`
            },
            data: {
              contractId: context.params.docId,
              contractName: contractName,
              userName: assignedTech,
              createdBy: createdBy,
              type: 'contract_update'
            },
            android: {
              priority: "normal",
              notification: {
                channelId: "contracts",
                priority: "normal",
                defaultSound: true,
                icon: "ic_notification"
              }
            }
          };
          const nTitle = '📋 New Contract Added';
          const nBody = `${createdBy} added ${contractName} to their contracts`;
          await saveNotificationHistory('ian', nTitle, nBody, 'contract_update', msg.data);
          await saveNotificationHistory('kristine', nTitle, nBody, 'contract_update', msg.data);
          console.log("✅ Contract own-list in-app notifications saved (ian/kristine):", context.params.docId);
          return null;
        }

        // Cross-assign: Ian/Kristine -> notify owner tech (not sender)
        if (createdLower !== 'kristine' && createdLower !== 'ian') return null;
        if (!ownerLower || ownerLower === createdLower) return null;

        await saveNotificationHistory(assignedTech.toLowerCase(), '📋 New Contract Assigned',
          `${contractName} has been assigned to you`, 'contract_update', {
            contractId: context.params.docId,
            contractName: contractName,
            userName: assignedTech,
            createdBy: createdBy,
            type: 'contract_update'
          });
        console.log("✅ New contract in-app notification saved for", assignedTech, ":", context.params.docId);
        return null;
      } catch (error) {
        console.error("❌ Error sending new contract notification:", error);
        throw error;
      }
    });
}

exports.onJamesContractCreated = createContractCreatedFunction('James Contracts');
exports.onIanContractCreated = createContractCreatedFunction('Ian Contracts');
exports.onDeanContractCreated = createContractCreatedFunction('Dean Contracts');

// ✅ 5. WORKVIEW UPDATES (in-app only)
// Rules:
// - If Ian or Kristine adds to someone else’s workview: the owner receives a notification (not the sender)
function createWorkViewNotifyFunction(collectionName) {
  return functions.firestore
    .document(collectionName + '/{docId}')
    .onCreate(async (snap, context) => {
      // Disabled: WorkView update in-app notifications are now written by the Android client
      // (so they work without Cloud Functions, and to avoid duplicate notification records).
      return null;

      const data = snap.data();
      const owner = data?.userName || '';
      const createdBy = (data?.createdBy || '').trim();
      const eventName = data?.eventName || 'Event';
      const eventType = data?.eventType || 'event';
      const eventDate = data?.date || '';
      const eventTime = data?.time || '';

      if (!owner || owner.toLowerCase() === createdBy.toLowerCase()) {
        return null;
      }
      // Only notify when Ian or Kristine updates the work view
      if (createdBy.toLowerCase() !== 'kristine' && createdBy.toLowerCase() !== 'ian') {
        return null;
      }

      const notificationTitle = `📅 Work View Updated`;
      const notificationBody = `${eventName} (${eventType}) added for ${eventDate} at ${eventTime}`;

      try {
        // In-app only notifications
        await saveNotificationHistory(owner.toLowerCase(), notificationTitle, notificationBody,
          'workview_update', {
            eventId: context.params.docId,
            eventName: eventName,
            eventType: eventType,
            eventDate: eventDate,
            eventTime: eventTime,
            createdBy: createdBy,
            targetUser: owner,
            type: 'workview_update'
          });
        console.log("✅ WorkView in-app notification saved for", owner);
        return null;
      } catch (error) {
        console.error("❌ Error sending WorkView notification:", error);
        throw error;
      }
    });
}

exports.onJamesWorkViewUpdate = createWorkViewNotifyFunction('james_workview');
exports.onIanWorkViewUpdate = createWorkViewNotifyFunction('ian_workview');
exports.onDeanWorkViewUpdate = createWorkViewNotifyFunction('dean_workview');

// ✅ 6. WORK EVENT REMINDERS → Send reminders only to the specific user 30 minutes before events
exports.sendWorkEventReminders = functions.pubsub
  .schedule('every 5 minutes')
  .onRun(async (context) => {
    const now = new Date();
    
    try {
      // Get all events for all users
      const eventsSnapshot = await admin.firestore()
        .collectionGroup('Events')
        .where('status', '==', 'scheduled')
        .get();

      eventsSnapshot.forEach(doc => {
        const data = doc.data();
        const eventTime = data?.time;
        const eventDate = data?.date;
        const eventName = data?.name;
        const eventAddress = data?.address;
        const eventType = data?.type || 'Work Event';
        
        if (eventTime && eventDate && eventName) {
          // Parse event time and date
          const [hours, minutes] = eventTime.split(':').map(Number);
          const [day, month, year] = eventDate.split('/').map(Number);
          
          const eventDateTime = new Date(year, month - 1, day, hours, minutes);
          const timeDiff = eventDateTime.getTime() - now.getTime();
          
          // Send reminder if event is within 30 minutes (and not past)
          if (timeDiff > 0 && timeDiff <= 30 * 60 * 1000) {
            // In-app only: no push reminders
          }
        }
      });

      return null;
    } catch (error) {
      console.error("❌ Error sending work event reminders:", error);
      throw error;
    }
  });

// ✅ 7. DELETE EXPIRED MESSAGES → Remove non-urgent messages after 30 minutes
exports.deleteExpiredMessages = functions.pubsub
  .schedule('every 5 minutes')
  .onRun(async (context) => {
    const now = new Date();
    const expiryCutoff = new Date(now.getTime() - 30 * 60 * 1000);

    try {
      const snapshot = await admin.firestore()
        .collectionGroup('messages')
        .where('createdAt', '<', expiryCutoff)
        .get();

      const deletePromises = [];
      snapshot.forEach(doc => {
        const data = doc.data();
        if (data?.isUrgent === true) return;
        deletePromises.push(doc.ref.delete());
      });

      if (deletePromises.length > 0) {
        await Promise.all(deletePromises);
        console.log(`✅ Deleted ${deletePromises.length} expired messages`);
      }
      return null;
    } catch (error) {
      console.error("❌ Error deleting expired messages:", error);
      throw error;
    }
  });

// ✅ 8. COMMISSION (Leads) NOTIFICATIONS (in-app only)
// Rules:
// - If James or Dean adds commission (new Lead): Ian and Kristine receive a notification
// - If Ian or Kristine edits commission: the affected technician (Added By) receives a notification (include old -> new)
exports.onLeadCreatedNotifyCommission = functions.firestore
  .document('Leads/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};
    const createdBy = (data['Created By'] || data['Added By'] || '').toString().trim();
    const commission = Number(data['Commission'] ?? 0);

    const createdLower = createdBy.toLowerCase();
    if (createdLower !== 'james' && createdLower !== 'dean') return null;

    const premise = (data['Premise Name'] || data['Premise'] || 'Lead').toString();
    const title = '💰 Commission Added';
    const body = `${createdBy} added commission €${commission.toFixed(2)} for ${premise}`;

    const payload = {
      leadId: context.params.docId,
      premiseName: premise,
      commission: String(commission),
      createdBy: createdBy,
      addedBy: (data['Added By'] || '').toString().trim(),
      type: 'commission'
    };

    await saveNotificationHistory('ian', title, body, 'commission', payload);
    await saveNotificationHistory('kristine', title, body, 'commission', payload);
    console.log('✅ Commission add in-app notification saved for Ian + Kristine');
    return null;
  });

exports.onLeadUpdatedNotifyCommission = functions.firestore
  .document('Leads/{docId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};

    const beforeCommission = Number(before['Commission'] ?? 0);
    const afterCommission = Number(after['Commission'] ?? 0);
    if (beforeCommission === afterCommission) return null;

    const editor = (after['Last Edited By'] || after['Edited By'] || after['Updated By'] || '').toString().trim();
    const editorLower = editor.toLowerCase();
    if (editorLower !== 'ian' && editorLower !== 'kristine') return null;

    const affectedTech = (after['Added By'] || '').toString().trim();
    if (!affectedTech) return null;
    if (affectedTech.toLowerCase() === editorLower) return null;

    const premise = (after['Premise Name'] || after['Premise'] || 'Lead').toString();
    const title = '💰 Commission Updated';
    const body = `${editor} updated commission for ${premise}: €${beforeCommission.toFixed(2)} → €${afterCommission.toFixed(2)}`;

    const payload = {
      leadId: context.params.docId,
      premiseName: premise,
      oldCommission: String(beforeCommission),
      newCommission: String(afterCommission),
      editor: editor,
      affectedTech: affectedTech,
      type: 'commission'
    };

    await saveNotificationHistory(affectedTech.toLowerCase(), title, body, 'commission', payload);
    console.log('✅ Commission update in-app notification saved for', affectedTech);
    return null;
  });

// ---------- AI Chat: key stored in Firestore AI-Chat/AI-API, field KEY. Only James can update via Update API Key. ----------
exports.updateOpenRouterKey = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Login required.');

  const callerEmail = (context.auth.token && context.auth.token.email) ? String(context.auth.token.email).trim() : '';
  const adminEmail = await getAdminEmail();
  if (!adminEmail || callerEmail.toLowerCase() !== adminEmail.toLowerCase()) {
    throw new functions.https.HttpsError('permission-denied', 'Only James can update the API key.');
  }

  const newKey = data && data.newKey;
  if (!newKey || typeof newKey !== 'string' || newKey.trim() === '') {
    throw new functions.https.HttpsError('invalid-argument', 'newKey must be a non-empty string');
  }

  await admin.firestore().doc(AI_CHAT_KEY_PATH).set({
    KEY: newKey.trim(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });
  console.log('AI-Chat/AI-API KEY updated');
  return { success: true };
});

// ---------- AI Chat: James can update Grok key (field key-grog) in same doc. ----------
exports.updateGrokKey = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Login required.');

  const callerEmail = (context.auth.token && context.auth.token.email) ? String(context.auth.token.email).trim() : '';
  const adminEmail = await getAdminEmail();
  if (!adminEmail || callerEmail.toLowerCase() !== adminEmail.toLowerCase()) {
    throw new functions.https.HttpsError('permission-denied', 'Only James can update the Grok key.');
  }

  const newKey = data && data.newKey;
  if (!newKey || typeof newKey !== 'string' || newKey.trim() === '') {
    throw new functions.https.HttpsError('invalid-argument', 'newKey must be a non-empty string');
  }

  await admin.firestore().doc(AI_CHAT_KEY_PATH).set({
    'key-grog': newKey.trim(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });
  console.log('AI-Chat/AI-API key-grog updated');
  return { success: true };
});

// ---------------------------------------------------------------------------
// 9. EMPLOYEE MANAGEMENT (super_admin only, callable from Android client)
// ---------------------------------------------------------------------------

function normalizeRole(raw) {
  if (!raw) return 'unknown';
  const s = String(raw).trim().toLowerCase();
  if (s === 'super_admin' || s === 'super admin' || s === 'owner') return 'super_admin';
  if (s === 'admin' || s === 'administrator') return 'admin';
  if (s === 'tech' || s === 'technician') return 'tech';
  return 'unknown';
}

async function requireSuperAdmin(context) {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Login required.');
  }
  const uid = context.auth.uid;
  const snap = await admin.firestore().collection('users').doc(uid).get();
  const data = snap && snap.exists ? snap.data() : null;
  const roleRaw = data && (data.role || data.Role);
  const roleNorm = normalizeRole(roleRaw);
  if (roleNorm !== 'super_admin') {
    throw new functions.https.HttpsError('permission-denied', 'Super admin only.');
  }
  return { uid, roleNorm, profile: data || {} };
}

// Creates a new Firebase Auth user and users/{uid} profile with role/flags.
exports.createEmployee = functions.https.onCall(async (data, context) => {
  await requireSuperAdmin(context);

  data = data || {};

  const name = (data.name || '').toString().trim();
  const number = (data.number || '').toString().trim();
  const email = (data.email || '').toString().trim().toLowerCase();
  const password = (data.password || '').toString();
  const staffId = (data.staffId || '').toString().trim();
  const contractKeyRaw = (data.contractKey || '').toString().trim();
  const title = (data.title || '').toString().trim();
  const roleNorm = normalizeRole(data.role);

  if (!name || !email || !password || !staffId || !contractKeyRaw) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'name, email, password, staffId and contractKey are required.'
    );
  }
  if (!['admin', 'tech', 'super_admin'].includes(roleNorm)) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'role must be one of admin, tech, super_admin.'
    );
  }

  // Contract key is stored normalized to lowercase for use in contracts.assignedTech.
  const contractKey = contractKeyRaw.toLowerCase();

  let userRecord;
  try {
    userRecord = await admin.auth().createUser({
      email,
      password,
      displayName: name,
    });
  } catch (e) {
    if (e && e.code === 'auth/email-already-exists') {
      throw new functions.https.HttpsError('already-exists', 'A user with this email already exists.');
    }
    throw new functions.https.HttpsError(
      'internal',
      'Failed to create auth user: ' + (e && e.message ? e.message : String(e))
    );
  }

  const uid = userRecord.uid;
  const isSuperAdmin = roleNorm === 'super_admin';
  const isAdmin = isSuperAdmin || roleNorm === 'admin';

  // Role-based defaults, aligned with SessionManager.
  const canSearch = isSuperAdmin;
  const canUseLocationFinder = isSuperAdmin;
  const canHardPressContracts = isAdmin;
  const canMarkPaidLeads = isSuperAdmin;
  const canAccessCommission = true;
  const seesAllJobs = isAdmin;
  const canSeeContracts = ['tech', 'admin', 'super_admin'].includes(roleNorm);
  const canViewAllContracts = isAdmin;

  const profile = {
    uid,
    name,
    email,
    number,
    title,
    staffId,
    contractKey,
    role: roleNorm,
    canSearch,
    canAccessCommission,
    canHardPressContracts,
    canMarkPaidLeads,
    canUseLocationFinder,
    seesAllJobs,
    canSeeContracts,
    canViewAllContracts,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    active: true,
  };

  await admin.firestore().collection('users').doc(uid).set(profile, { merge: true });
  return { uid };
});

// Initialize or backfill a users/{uid} profile without overwriting existing values.
exports.initializeEmployeeProfile = functions.https.onCall(async (data, context) => {
  await requireSuperAdmin(context);

  data = data || {};

  let targetUid = (data.uid || '').toString().trim();
  const email = (data.email || '').toString().trim().toLowerCase();

  if (!targetUid && !email) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Either uid or email must be provided.'
    );
  }

  if (!targetUid && email) {
    try {
      const userRecord = await admin.auth().getUserByEmail(email);
      targetUid = userRecord.uid;
    } catch (e) {
      throw new functions.https.HttpsError(
        'not-found',
        'No auth user found for the given email.'
      );
    }
  }

  const docRef = admin.firestore().collection('users').doc(targetUid);
  const snap = await docRef.get();
  const existing = snap.exists ? (snap.data() || {}) : {};

  const updates = {};

  // Preserve existing values; only fill in when missing.
  const incomingRoleNorm = normalizeRole(data.role);
  const existingRoleRaw = existing.role || existing.Role;
  const roleNorm = normalizeRole(existingRoleRaw || incomingRoleNorm);

  function hasValue(obj, key) {
    return Object.prototype.hasOwnProperty.call(obj, key) && obj[key] !== undefined && obj[key] !== null;
  }

  function valueOrEmpty(obj, key) {
    const v = hasValue(obj, key) ? obj[key] : '';
    return (v === undefined || v === null) ? '' : String(v).trim();
  }

  const incoming = {
    name: (data.name || '').toString().trim(),
    email: email || (data.email || '').toString().trim(),
    number: (data.number || '').toString().trim(),
    title: (data.title || '').toString().trim(),
    staffId: (data.staffId || '').toString().trim(),
    contractKey: (data.contractKey || '').toString().trim(),
  };

  // Simple string fields.
  ['name', 'email', 'number', 'title', 'staffId'].forEach(key => {
    const current = valueOrEmpty(existing, key);
    const incomingVal = incoming[key];
    if (!current && incomingVal) {
      updates[key] = incomingVal;
    }
  });

  // Contract key: ensure lowercase when we do populate it.
  const existingCk = valueOrEmpty(existing, 'contractKey');
  if (!existingCk && incoming.contractKey) {
    updates.contractKey = incoming.contractKey.toLowerCase();
  }

  // Role: set only if missing and we have a sensible default.
  const existingRole = valueOrEmpty(existing, 'role') || valueOrEmpty(existing, 'Role');
  if (!existingRole && roleNorm !== 'unknown') {
    updates.role = roleNorm;
  }

  const effRole = existingRole ? normalizeRole(existingRole) : roleNorm;

  const isSuperAdmin = effRole === 'super_admin';
  const isAdmin = isSuperAdmin || effRole === 'admin';

  // Role-based defaults for flags; only add when the field is absent.
  const defaults = {
    canSearch: isSuperAdmin,
    canUseLocationFinder: isSuperAdmin,
    canHardPressContracts: isAdmin,
    canMarkPaidLeads: isSuperAdmin,
    canAccessCommission: true,
    seesAllJobs: isAdmin,
    canSeeContracts: ['tech', 'admin', 'super_admin'].includes(effRole),
    canViewAllContracts: isAdmin,
  };

  Object.keys(defaults).forEach(key => {
    if (!hasValue(existing, key)) {
      updates[key] = defaults[key];
    }
  });

  if (!hasValue(existing, 'createdAt')) {
    updates.createdAt = admin.firestore.FieldValue.serverTimestamp();
  }
  if (!hasValue(existing, 'active')) {
    updates.active = true;
  }

  if (Object.keys(updates).length === 0) {
    // Nothing to change; return success without write.
    return { uid: targetUid, updated: false };
  }

  await docRef.set(updates, { merge: true });
  return { uid: targetUid, updated: true };
});

