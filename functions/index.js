const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// ✅ 1. ENHANCED MESSAGE PUSH with better content and reliability
exports.onMessageCreated = functions.firestore
  .document('messages/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const sender = data?.sender || 'Unknown';
    const messageBody = data?.body || '';
    const timestamp = data?.timestamp?.toDate?.() || context.timestamp?.toDate?.();
    const now = new Date();
    const ageInSeconds = timestamp ? (now - timestamp) / 1000 : 9999;

    // Skip old messages to prevent spam
    if (!sender || ageInSeconds > 10) {
      console.log("⏩ Skipping old or invalid message:", context.params.docId, "age:", ageInSeconds);
      return null;
    }

    // Create rich notification content
    const notificationTitle = `💬 New Message from ${sender}`;
    const notificationBody = messageBody.length > 100 ? 
      messageBody.substring(0, 100) + '...' : messageBody;

    const message = {
      notification: {
        title: notificationTitle,
        body: notificationBody
      },
      data: {
        sender: sender,
        messageId: context.params.docId,
        timestamp: timestamp ? timestamp.toISOString() : new Date().toISOString(),
        type: 'message'
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
      },
      condition: `'all' in topics && !('${sender.toLowerCase()}' in topics)`
    };

    try {
      const response = await admin.messaging().send(message);
      console.log("✅ Message notification sent successfully:", {
        messageId: context.params.docId,
        sender: sender,
        response: response
      });
      return response;
    } catch (error) {
      console.error("❌ Error sending message notification:", {
        error: error.message,
        messageId: context.params.docId,
        sender: sender
      });
      throw error;
    }
  });

// ✅ 2. ENHANCED JOBWORK → Standard Field Technician Jobs
exports.onJobWorkAdded = functions.firestore
  .document('JobWork/{docId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const tech = data?.AssignedTech || 'Technician';
    const customer = data?.CustomerName || 'Customer';
    const jobType = data?.JobType || 'Service';
    const createdBy = data?.CreatedBy || '';
    const createdAt = data?.CreatedAt?.toDate?.();
    const now = new Date();
    const ageInSeconds = createdAt ? (now - createdAt) / 1000 : 9999;

    if (ageInSeconds > 15) {
      console.log("⏩ Skipping old JobWork entry:", context.params.docId);
      return null;
    }

    const notificationTitle = `🚐 New Job Assignment`;
    const notificationBody = `${jobType} job for ${customer} assigned to ${tech}`;

    const message = {
      notification: {
        title: notificationTitle,
        body: notificationBody
      },
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
      },
      condition: `'all' in topics && !('${createdBy.toLowerCase()}' in topics)`
    };

    try {
      const response = await admin.messaging().send(message);
      console.log("✅ JobWork notification sent successfully:", {
        jobId: context.params.docId,
        tech: tech,
        customer: customer,
        response: response
      });
      return response;
    } catch (error) {
      console.error("❌ Error sending JobWork notification:", {
        error: error.message,
        jobId: context.params.docId
      });
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

    const message = {
      notification: {
        title: notificationTitle,
        body: notificationBody
      },
      data: {
        jobId: context.params.docId,
        assignedManager: manager,
        task: task,
        priority: priority,
        createdBy: createdBy,
        type: 'management'
      },
      android: {
        priority: "high",
        notification: {
          channelId: "management",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
          icon: "ic_notification"
        }
      },
      condition: `'all' in topics && !('${createdBy.toLowerCase()}' in topics)`
    };

    try {
      const response = await admin.messaging().send(message);
      console.log("✅ ManagmentJob notification sent successfully:", {
        jobId: context.params.docId,
        manager: manager,
        task: task,
        priority: priority,
        response: response
      });
      return response;
    } catch (error) {
      console.error("❌ Error sending ManagmentJob notification:", {
        error: error.message,
        jobId: context.params.docId
      });
      throw error;
    }
  });

// ✅ 4. NEW: CONTRACT UPDATES → Notify when contracts are modified
exports.onContractUpdated = functions.firestore
  .document('{userName} Contracts/{docId}')
  .onUpdate(async (change, context) => {
    const beforeData = change.before.data();
    const afterData = change.after.data();
    const userName = context.params.userName;
    
    // Check if lastVisit was updated
    if (beforeData?.lastVisit !== afterData?.lastVisit) {
      const contractName = afterData?.name || 'Contract';
      const lastVisit = afterData?.lastVisit || 'N/A';
      
      const notificationTitle = `📅 Contract Updated`;
      const notificationBody = `${contractName} - Last visit: ${lastVisit}`;

      const message = {
        notification: {
          title: notificationTitle,
          body: notificationBody
        },
        data: {
          contractId: context.params.docId,
          contractName: contractName,
          lastVisit: lastVisit,
          userName: userName,
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
        },
        condition: `'all' in topics`
      };

      try {
        const response = await admin.messaging().send(message);
        console.log("✅ Contract update notification sent:", {
          contractId: context.params.docId,
          contractName: contractName,
          lastVisit: lastVisit,
          response: response
        });
        return response;
      } catch (error) {
        console.error("❌ Error sending contract update notification:", error);
        throw error;
      }
    }
    
    return null;
  });

// ✅ 5. WORK EVENT REMINDERS → Send reminders only to the specific user 30 minutes before events
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

      const reminderPromises = [];

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
            const notificationTitle = `⏰ Work Event Reminder`;
            const notificationBody = `${eventName} starts in 30 minutes at ${eventTime}`;

            const message = {
              notification: {
                title: notificationTitle,
                body: notificationBody
              },
              data: {
                eventId: doc.id,
                eventName: eventName,
                eventTime: eventTime,
                eventDate: eventDate,
                eventAddress: eventAddress || '',
                eventType: eventType,
                type: 'work_event_reminder'
              },
              android: {
                priority: "high",
                notification: {
                  channelId: "work_reminders",
                  priority: "high",
                  defaultSound: true,
                  defaultVibrateTimings: true,
                  icon: "ic_notification"
                }
              },
              // Send only to the specific user's topic
              topic: doc.ref.parent.parent.id.toLowerCase()
            };

            reminderPromises.push(admin.messaging().send(message));
          }
        }
      });

      if (reminderPromises.length > 0) {
        await Promise.all(reminderPromises);
        console.log(`✅ Sent ${reminderPromises.length} work event reminders`);
      }

      return null;
    } catch (error) {
      console.error("❌ Error sending work event reminders:", error);
      throw error;
    }
  });


