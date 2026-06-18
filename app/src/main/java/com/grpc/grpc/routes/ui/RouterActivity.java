package com.grpc.grpc.routes.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.routes.model.RouteStop;
import com.grpc.grpc.routes.pdf.BestRoutePdfGenerator;
import com.grpc.grpc.routes.util.GoogleDirectionsClient;
import com.grpc.grpc.routes.util.RouteFunctionsClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouterActivity extends AppCompatActivity {
    private static final int MAX_ROUTE_SIZE = 10;
    private static final boolean USE_SERVER_OPTIMIZER_FOR_GAPS = false;
    private static final int REQUEST_LOCATION_PERMISSION = 2004;
    private static final double ESTIMATED_SPEED_KMH = 45d;
    private static final int DAY_START_MINUTES = (8 * 60) + 30;
    private static final int DAY_END_SOFT_MINUTES = (17 * 60) + 30;
    private static final int DAY_END_HARD_MINUTES = 18 * 60;
    private static final int CONTRACT_SERVICE_MINUTES = 30;
    private static final int CONTRACT_SLOT_MINUTES = 45;
    private static final int START_OPTION_CURRENT_LOCATION = 0;
    private static final int START_OPTION_HOME_ADDRESS = 1;
    private static final int START_OPTION_NO_START = 2;

    private Spinner ownerSpinner;
    private TextView targetOwnerLabel;
    private TextView selectedDayText;
    private TextView statusText;
    private TextView resultsText;
    private TextView jobsSummaryText;
    private android.widget.Button createButton;
    private android.widget.Button openGoogleRouteButton;
    private android.widget.Button viewRoutesButton;
    private android.widget.Button selectDayButton;
    private android.widget.Button addJobButton;
    private android.widget.Button clearJobsButton;

    private final List<StaffDirectory.OwnerOption> ownerOptions = new ArrayList<>();
    private final List<RouteStop> manualJobs = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
    private final SimpleDateFormat routeDayFormat = new SimpleDateFormat("EEEE dd/MM/yyyy", Locale.getDefault());
    private final Calendar selectedRouteDay = Calendar.getInstance();

    private FirebaseFirestore db;
    private GoogleDirectionsClient directionsClient;
    private RouteFunctionsClient routeFunctionsClient;
    @Nullable
    private SessionManager.Session session;
    @Nullable
    private StaffDirectory.OwnerOption pendingOwnerForPermission;
    @Nullable
    private RouteOptions pendingRouteOptions;
    @Nullable
    private List<RouteStop> lastGeneratedRoute;
    private String routeProviderWarning = "";
    private String routeDiagnostics = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_router);

        db = FirebaseHelper.getFirestore();
        directionsClient = new GoogleDirectionsClient(this);
        routeFunctionsClient = new RouteFunctionsClient();
        ownerSpinner = findViewById(R.id.routerOwnerSpinner);
        targetOwnerLabel = findViewById(R.id.routerTargetOwnerLabel);
        selectedDayText = findViewById(R.id.routerSelectedDayText);
        statusText = findViewById(R.id.routerStatusText);
        resultsText = findViewById(R.id.routerResultsText);
        jobsSummaryText = findViewById(R.id.routerJobsSummaryText);
        createButton = findViewById(R.id.buttonCreateBestRoute);
        openGoogleRouteButton = findViewById(R.id.buttonOpenGoogleRoute);
        viewRoutesButton = findViewById(R.id.buttonViewCreatedRoutes);
        selectDayButton = findViewById(R.id.buttonSelectRouteDay);
        addJobButton = findViewById(R.id.buttonAddRouteJob);
        clearJobsButton = findViewById(R.id.buttonClearRouteJobs);

        if (selectDayButton != null) {
            selectDayButton.setOnClickListener(v -> showRouteDayPicker());
        }
        if (viewRoutesButton != null) {
            viewRoutesButton.setOnClickListener(v -> openCreatedRoutes());
        }
        if (addJobButton != null) {
            addJobButton.setOnClickListener(v -> showAddJobDialog());
        }
        if (clearJobsButton != null) {
            clearJobsButton.setOnClickListener(v -> {
                manualJobs.clear();
                refreshJobsSummary();
            });
        }
        if (createButton != null) {
            createButton.setOnClickListener(v -> createRoute());
            createButton.setEnabled(false);
        }
        if (openGoogleRouteButton != null) {
            openGoogleRouteButton.setOnClickListener(v -> openCurrentRouteInGoogleMaps());
            openGoogleRouteButton.setEnabled(false);
        }
        updateSelectedDayText();
        refreshJobsSummary();

        SessionManager.ensureLoaded(this, loadedSession -> runOnUiThread(() -> {
            session = loadedSession;
            if (loadedSession == null || !loadedSession.canRoute) {
                Toast.makeText(this, "Router is not enabled for this profile.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
                Toast.makeText(this, "Router is unavailable in offline / restricted mode.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            configureOwnerPicker(loadedSession);
        }));
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSION) return;

        StaffDirectory.OwnerOption owner = pendingOwnerForPermission;
        RouteOptions options = pendingRouteOptions;
        pendingOwnerForPermission = null;
        pendingRouteOptions = null;

        if (owner == null || options == null) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(this, "Location not allowed. Building route without your live start point.", Toast.LENGTH_SHORT).show();
            options.startMode = START_OPTION_NO_START;
        }
        beginRouteCreation(owner, options);
    }

    private void configureOwnerPicker(SessionManager.Session loadedSession) {
        if (loadedSession.isAdmin) {
            setStatus("Loading available users...");
            StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
                ownerOptions.clear();
                if (options != null) ownerOptions.addAll(options);
                if (ownerOptions.isEmpty()) {
                    setStatus("No route owners found.");
                    if (createButton != null) createButton.setEnabled(false);
                    return;
                }
                bindOwnerSpinner(false);
                setStatus("Choose a user and create a best route.");
            }));
            return;
        }

        ownerOptions.clear();
        String ownerKey = loadedSession.contractKey != null ? loadedSession.contractKey.trim() : "";
        if (ownerKey.isEmpty()) ownerKey = loadedSession.staffId != null ? loadedSession.staffId.trim() : "";
        if (ownerKey.isEmpty()) {
            setStatus("Your profile is missing a contract key.");
            if (createButton != null) createButton.setEnabled(false);
            return;
        }
        ownerOptions.add(new StaffDirectory.OwnerOption(loadedSession.staffId, ownerKey, StaffDirectory.capitalizeContractKey(ownerKey)));
        bindOwnerSpinner(true);
        setStatus("Ready to build your best route.");
    }

    private void bindOwnerSpinner(boolean lockToCurrentUser) {
        List<String> labels = new ArrayList<>();
        for (StaffDirectory.OwnerOption option : ownerOptions) {
            labels.add(option.display != null && !option.display.trim().isEmpty()
                    ? option.display
                    : StaffDirectory.capitalizeContractKey(option.ownerKey));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ownerSpinner.setAdapter(adapter);
        ownerSpinner.setEnabled(!lockToCurrentUser);
        ownerSpinner.setClickable(!lockToCurrentUser);
        if (targetOwnerLabel != null) {
            targetOwnerLabel.setText(lockToCurrentUser ? "Routing your contracts" : "Choose a user to route");
        }
        if (createButton != null) createButton.setEnabled(true);
    }

    private void showRouteDayPicker() {
        Calendar initial = (Calendar) selectedRouteDay.clone();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedRouteDay.set(Calendar.YEAR, year);
            selectedRouteDay.set(Calendar.MONTH, month);
            selectedRouteDay.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateSelectedDayText();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateSelectedDayText() {
        if (selectedDayText == null) return;
        Calendar today = Calendar.getInstance();
        boolean sameDay = today.get(Calendar.YEAR) == selectedRouteDay.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == selectedRouteDay.get(Calendar.DAY_OF_YEAR);
        String label = routeDayFormat.format(selectedRouteDay.getTime());
        if (sameDay) {
            label = "Today - " + label;
        }
        selectedDayText.setText("Selected day: " + label + "\nDay runs from 08:30 to 17:30/18:00.");
    }

    private void createRoute() {
        StaffDirectory.OwnerOption selectedOwner = getSelectedOwner();
        if (selectedOwner == null || TextUtils.isEmpty(selectedOwner.ownerKey)) {
            Toast.makeText(this, "Choose a valid user first.", Toast.LENGTH_SHORT).show();
            return;
        }
        showRouteOptionsDialog(selectedOwner);
    }

    private void showRouteOptionsDialog(StaffDirectory.OwnerOption selectedOwner) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding / 2, padding, 0);

        TextView prompt = new TextView(this);
        prompt.setText("Choose how the route should start. The day is planned from 08:30 and aims to finish back at your start point by 17:30-18:00.");
        prompt.setPadding(0, 0, 0, dpToPx(8));
        container.addView(prompt);

        Spinner startSpinner = new Spinner(this);
        ArrayAdapter<String> startAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Use my current location", "Use a home address", "Continue without start location"});
        startAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        startSpinner.setAdapter(startAdapter);
        container.addView(startSpinner);

        EditText homeAddressInput = new EditText(this);
        homeAddressInput.setHint("Home address");
        homeAddressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        homeAddressInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = dpToPx(12);
        homeAddressInput.setLayoutParams(inputParams);
        container.addView(homeAddressInput);

        CheckBox returnToStartCheck = new CheckBox(this);
        returnToStartCheck.setText("Finish back at the starting point by the end of the day");
        returnToStartCheck.setChecked(true);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        checkParams.topMargin = dpToPx(12);
        returnToStartCheck.setLayoutParams(checkParams);
        container.addView(returnToStartCheck);

        startSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                homeAddressInput.setVisibility(position == START_OPTION_HOME_ADDRESS ? View.VISIBLE : View.GONE);
                if (position == START_OPTION_NO_START) {
                    returnToStartCheck.setChecked(false);
                    returnToStartCheck.setEnabled(false);
                } else {
                    returnToStartCheck.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Best Route Options")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> {
                    RouteOptions options = new RouteOptions();
                    options.startMode = startSpinner.getSelectedItemPosition();
                    options.homeAddress = homeAddressInput.getText() != null ? homeAddressInput.getText().toString().trim() : "";
                    options.routeStartMinutes = DAY_START_MINUTES;
                    options.routeEndMinutes = DAY_END_SOFT_MINUTES;
                    options.routeHardEndMinutes = DAY_END_HARD_MINUTES;
                    options.routeDateMillis = selectedRouteDay.getTimeInMillis();
                    options.returnToStart = returnToStartCheck.isChecked();
                    if (options.startMode == START_OPTION_HOME_ADDRESS && TextUtils.isEmpty(options.homeAddress)) {
                        Toast.makeText(this, "Enter a home address or continue without a start location.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    beginRouteCreation(selectedOwner, options);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void beginRouteCreation(StaffDirectory.OwnerOption owner, RouteOptions options) {
        if (options.startMode == START_OPTION_CURRENT_LOCATION) {
            if (!hasLocationPermission()) {
                pendingOwnerForPermission = owner;
                pendingRouteOptions = options;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
                return;
            }
            resolveCurrentLocationAndCreateRoute(owner, options);
            return;
        }
        fetchContractsAndBuildRoute(owner, options, null);
    }

    private void resolveCurrentLocationAndCreateRoute(StaffDirectory.OwnerOption owner, RouteOptions options) {
        setBusy(true);
        setStatus("Getting your current location...");
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        client.getLastLocation()
                .addOnSuccessListener(location -> {
                    RouteAnchor anchor = null;
                    if (location != null) {
                        anchor = new RouteAnchor("Current Location", "Device location", location.getLatitude(), location.getLongitude());
                    } else {
                        Toast.makeText(this, "Could not get current location. Building route without it.", Toast.LENGTH_SHORT).show();
                        options.startMode = START_OPTION_NO_START;
                    }
                    fetchContractsAndBuildRoute(owner, options, anchor);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not get current location. Building route without it.", Toast.LENGTH_SHORT).show();
                    options.startMode = START_OPTION_NO_START;
                    fetchContractsAndBuildRoute(owner, options, null);
                });
    }

    private void fetchContractsAndBuildRoute(StaffDirectory.OwnerOption owner, RouteOptions options, @Nullable RouteAnchor initialAnchor) {
        setBusy(true);
        setStatus("Loading contracts for " + owner.display + "...");
        db.collection(FirestorePaths.CONTRACTS)
                .get()
                .addOnSuccessListener(snapshot -> worker.execute(() -> {
                    RouteAnchor anchor = initialAnchor;
                    if (options.startMode == START_OPTION_HOME_ADDRESS && !TextUtils.isEmpty(options.homeAddress)) {
                        anchor = geocodeRouteAnchor("Home", options.homeAddress);
                        if (anchor == null) {
                            runOnUiThread(() -> Toast.makeText(this, "Could not find that home address. Continuing without it.", Toast.LENGTH_SHORT).show());
                        }
                    }

                    List<RouteStop> ownerContracts = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot ds : snapshot.getDocuments()) {
                            RouteStop stop = mapRouteStop(ds, owner.ownerKey);
                            if (stop != null) ownerContracts.add(stop);
                        }
                    }
                    processContracts(owner, ownerContracts, cloneManualJobs(), anchor, options);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    setBusy(false);
                    setStatus("Failed to load contracts.");
                    Toast.makeText(this, "Could not load contracts.", Toast.LENGTH_SHORT).show();
                }));
    }

    private void processContracts(StaffDirectory.OwnerOption owner,
                                  List<RouteStop> contracts,
                                  List<RouteStop> jobs,
                                  @Nullable RouteAnchor anchor,
                                  RouteOptions options) {
        if (contracts.isEmpty() && jobs.isEmpty()) {
            runOnUiThread(() -> {
                setBusy(false);
                setStatus("No jobs or contracts found for " + owner.display + ".");
                resultsText.setText("");
            });
            return;
        }

        runOnUiThread(() -> setStatus("Geocoding missing contract addresses..."));
        geocodeMissingContracts(contracts);
        geocodeStops(jobs);
        routeProviderWarning = "";
        routeDiagnostics = "";
        for (RouteStop contract : contracts) {
            if (contract != null) {
                contract.behind = isPastDueOnDay(contract.nextVisit, options.routeDateMillis);
            }
        }

        List<RouteStop> prioritized = prioritizeContracts(contracts);
        int contractsAllowed = Math.max(0, MAX_ROUTE_SIZE - jobs.size());
        List<RouteStop> limited = selectClosestContracts(prioritized, jobs, anchor, contractsAllowed);
        int limitedMissingCoords = 0;
        for (RouteStop stop : limited) {
            if (stop == null || !stop.hasCoordinates()) limitedMissingCoords++;
        }
        List<RouteStop> ordered = buildTimedRoute(limited, jobs, anchor, options);
        int apiWaypointsSent = 0;
        int apiWaypointsSkipped = 0;
        if (ordered.size() > 2) {
            for (int i = 1; i < ordered.size() - 1; i++) {
                RouteStop stop = ordered.get(i);
                if (stop != null && stop.hasCoordinates()) {
                    apiWaypointsSent++;
                } else {
                    apiWaypointsSkipped++;
                }
            }
        }
        routeDiagnostics = "Route diagnostics: selected " + limited.size() + " closest contracts from " + prioritized.size()
                + " candidates"
                + (limitedMissingCoords > 0 ? " (" + limitedMissingCoords + " missing coords)" : "")
                + ". API waypoints sent: " + apiWaypointsSent
                + (apiWaypointsSkipped > 0 ? " (skipped " + apiWaypointsSkipped + " invalid)." : ".");
        boolean usedGoogleRoute = false;
        if (routeFunctionsClient != null) {
            try {
                usedGoogleRoute = routeFunctionsClient.populateLegs(ordered);
            } catch (Exception e) {
                rememberRouteProviderWarning(e);
            }
        }
        final boolean usedServerRoute = usedGoogleRoute;
        recalculatePlannedWindows(ordered, options);
        File pdfFile = BestRoutePdfGenerator.generateBestRoutePdf(this, owner.display, ordered, new Date(options.routeDateMillis));

        runOnUiThread(() -> {
            setBusy(false);
            if (pdfFile == null) {
                setStatus("Failed to create route PDF.");
                return;
            }
            lastGeneratedRoute = new ArrayList<>(ordered);
            if (openGoogleRouteButton != null) {
                openGoogleRouteButton.setEnabled(lastGeneratedRoute.size() > 1);
            }
            setStatus("Created " + pdfFile.getName() + " in BEST ROUTES."
                    + (usedServerRoute ? " Server route timings applied." : " Using fallback travel estimates.")
                    + (routeProviderWarning.isEmpty() ? "" : " " + routeProviderWarning));
            resultsText.setText(buildPreviewText(owner.display, ordered, pdfFile, options, usedServerRoute));
        });
    }

    @Nullable
    private RouteStop mapRouteStop(DocumentSnapshot ds, String ownerKey) {
        if (ds == null || !ds.exists()) return null;
        String assignedTech = safeString(ds.get("assignedTech"));
        if (assignedTech.isEmpty() || !assignedTech.equalsIgnoreCase(ownerKey)) return null;

        String documentId = ds.getId() != null ? ds.getId() : "";
        if ("_schema".equals(documentId)) return null;

        String name = safeString(ds.get("name"), ds.get("Name"), ds.get("customerName"), ds.get("CustomerName"));
        String address = safeString(ds.get("address"), ds.get("Address"));
        String nextVisit = safeString(ds.get("nextVisit"), ds.get("NextVisit"));
        if (nextVisit.isEmpty()) {
            nextVisit = calculateNextVisit(safeString(ds.get("lastVisit"), ds.get("LastVisit")), ds.get("visits"));
        }
        boolean behind = isPastDue(nextVisit);
        double lat = toDouble(ds.get("routeLat"));
        double lng = toDouble(ds.get("routeLng"));

        return new RouteStop(documentId, name, address, nextVisit, behind, assignedTech, false, false, lat, lng);
    }

    private void geocodeMissingContracts(List<RouteStop> contracts) {
        for (RouteStop stop : contracts) {
            if (stop == null || stop.hasCoordinates() || TextUtils.isEmpty(stop.address)) continue;
            try {
                geocodeStop(stop);
                if (!stop.hasCoordinates()) continue;
                db.collection(FirestorePaths.CONTRACTS)
                        .document(stop.documentId)
                        .update("routeLat", stop.latitude, "routeLng", stop.longitude, "routeGeocodedAt", System.currentTimeMillis());
            } catch (Exception ignored) {
            }
        }
    }

    private void geocodeStops(List<RouteStop> stops) {
        for (RouteStop stop : stops) {
            if (stop == null || stop.hasCoordinates() || TextUtils.isEmpty(stop.address)) continue;
            geocodeStop(stop);
        }
    }

    private void geocodeStop(RouteStop stop) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> matches = geocoder.getFromLocationName(stop.address, 1);
            if (matches == null || matches.isEmpty()) return;
            Address address = matches.get(0);
            stop.latitude = address.getLatitude();
            stop.longitude = address.getLongitude();
        } catch (Exception ignored) {
        }
    }

    private List<RouteStop> prioritizeContracts(List<RouteStop> contracts) {
        List<RouteStop> ordered = new ArrayList<>(contracts);
        ordered.sort((left, right) -> {
            if (left.behind != right.behind) return left.behind ? -1 : 1;
            long leftTime = parseRouteDate(left.nextVisit);
            long rightTime = parseRouteDate(right.nextVisit);
            if (leftTime != rightTime) return Long.compare(leftTime, rightTime);
            return safeString(left.name).compareToIgnoreCase(safeString(right.name));
        });
        return ordered;
    }

    private List<RouteStop> buildTimedRoute(List<RouteStop> contracts,
                                            List<RouteStop> jobs,
                                            @Nullable RouteAnchor anchor,
                                            RouteOptions options) {
        List<RouteStop> remainingContracts = new ArrayList<>(contracts);
        List<RouteStop> orderedJobs = new ArrayList<>(jobs);
        orderedJobs.sort((left, right) -> Integer.compare(left.requestedStartMinutes, right.requestedStartMinutes));
        List<RouteStop> ordered = new ArrayList<>();
        int currentMinutes = options.routeStartMinutes;
        RouteStop current = null;
        RouteStop returnTarget = anchor != null && options.returnToStart ? createAnchorStop(anchor, true) : null;

        if (anchor != null) {
            RouteStop start = createAnchorStop(anchor, false);
            start.plannedStartMinutes = currentMinutes;
            start.plannedEndMinutes = currentMinutes;
            start.legDistanceKm = 0d;
            start.estimatedMinutes = 0;
            ordered.add(start);
            current = start;
        }

        for (int i = 0; i < orderedJobs.size(); i++) {
            RouteStop job = orderedJobs.get(i);
            List<RouteStop> gapContracts = selectContractsForGap(
                    current,
                    currentMinutes,
                    job,
                    remainingContracts,
                    currentMinutes,
                    job.requestedStartMinutes,
                    true
            );
            gapContracts = optimizeGapContracts(current, job, gapContracts, job.requestedStartMinutes - currentMinutes);
            for (RouteStop contract : gapContracts) {
                scheduleContractBlock(contract, current, currentMinutes);
                ordered.add(contract);
                current = contract;
                currentMinutes = contract.plannedEndMinutes + Math.max(0, CONTRACT_SLOT_MINUTES - CONTRACT_SERVICE_MINUTES);
                remainingContracts.remove(contract);
            }

            scheduleManualJob(job, current, currentMinutes);
            ordered.add(job);
            current = job;
            currentMinutes = job.plannedEndMinutes;
        }

        List<RouteStop> finalContracts = selectContractsForGap(
                current,
                currentMinutes,
                returnTarget,
                remainingContracts,
                currentMinutes,
                options.routeEndMinutes,
                returnTarget != null
        );
        finalContracts = optimizeGapContracts(current, returnTarget, finalContracts, options.routeEndMinutes - currentMinutes);
        for (RouteStop contract : finalContracts) {
            scheduleContractBlock(contract, current, currentMinutes);
            ordered.add(contract);
            current = contract;
            currentMinutes = contract.plannedEndMinutes + Math.max(0, CONTRACT_SLOT_MINUTES - CONTRACT_SERVICE_MINUTES);
            remainingContracts.remove(contract);
        }

        if (returnTarget != null) {
            int anchorArrival = currentMinutes;
            if (current != null) {
                anchorArrival = Math.max(anchorArrival, currentMinutes + Math.max(0, estimateTravelMinutes(current, returnTarget)));
            }
            anchorArrival = Math.min(Math.max(anchorArrival, options.routeEndMinutes), options.routeHardEndMinutes);
            scheduleAnchorReturn(returnTarget, current, anchorArrival);
            ordered.add(returnTarget);
        }

        return ordered;
    }

    private RouteStop pickNearest(@Nullable RouteStop current, List<RouteStop> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (current == null || !current.hasCoordinates()) return candidates.get(0);

        RouteStop best = candidates.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (RouteStop candidate : candidates) {
            double distance = distanceBetweenKm(current, candidate);
            if (Double.isNaN(distance)) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private List<RouteStop> selectContractsForGap(@Nullable RouteStop current,
                                                  int currentMinutes,
                                                  @Nullable RouteStop target,
                                                  List<RouteStop> remainingContracts,
                                                  int gapStartMinutes,
                                                  int gapEndMinutes,
                                                  boolean reserveTargetTravel) {
        List<RouteStop> chosen = new ArrayList<>();
        if (remainingContracts.isEmpty()) return chosen;

        int availableMinutes = Math.max(0, gapEndMinutes - Math.max(currentMinutes, gapStartMinutes));
        if (reserveTargetTravel && target != null) {
            availableMinutes -= Math.max(CONTRACT_SLOT_MINUTES, estimateTravelMinutes(current, target));
        }
        int capacity = Math.max(0, Math.min(remainingContracts.size(), availableMinutes / CONTRACT_SLOT_MINUTES));
        if (capacity <= 0) return chosen;

        List<RouteStop> pool = new ArrayList<>(remainingContracts);
        RouteStop cursor = current;
        while (chosen.size() < Math.max(0, capacity - (target != null ? 1 : 0)) && !pool.isEmpty()) {
            RouteStop next = pickNearest(cursor, pool);
            if (next == null) break;
            chosen.add(next);
            cursor = next;
            pool.remove(next);
        }

        if (target != null && chosen.size() < capacity && !pool.isEmpty()) {
            RouteStop finalBeforeTarget = pickClosestToTarget(target, pool);
            if (finalBeforeTarget != null) {
                chosen.add(finalBeforeTarget);
            }
        } else {
            while (chosen.size() < capacity && !pool.isEmpty()) {
                RouteStop next = pickNearest(cursor, pool);
                if (next == null) break;
                chosen.add(next);
                cursor = next;
                pool.remove(next);
            }
        }
        return chosen;
    }

    private List<RouteStop> optimizeGapContracts(@Nullable RouteStop origin,
                                                 @Nullable RouteStop target,
                                                 List<RouteStop> contracts,
                                                 int availableMinutes) {
        if (contracts == null || contracts.isEmpty()) return contracts;
        if (!USE_SERVER_OPTIMIZER_FOR_GAPS) return contracts;
        if (routeFunctionsClient == null || origin == null || target == null) {
            return contracts;
        }

        try {
            List<RouteStop> optimized = new ArrayList<>(routeFunctionsClient.optimizeContractOrder(origin, target, contracts));
            int safetyMinutes = Math.max(0, availableMinutes);
            while (!optimized.isEmpty()) {
                int segmentDriveMinutes = routeFunctionsClient.estimateSegmentMinutes(origin, optimized, target);
                if (segmentDriveMinutes < 0) break;
                int totalSegmentMinutes = segmentDriveMinutes + (optimized.size() * CONTRACT_SERVICE_MINUTES);
                if (totalSegmentMinutes <= safetyMinutes) {
                    break;
                }
                optimized.remove(0);
                if (optimized.size() > 1) {
                    optimized = new ArrayList<>(routeFunctionsClient.optimizeContractOrder(origin, target, optimized));
                }
            }
            return optimized;
        } catch (Exception e) {
            rememberRouteProviderWarning(e);
            return contracts;
        }
    }

    private RouteStop pickClosestToTarget(RouteStop target, List<RouteStop> candidates) {
        if (target == null || candidates == null || candidates.isEmpty()) return null;
        if (!target.hasCoordinates()) return candidates.get(0);
        RouteStop best = candidates.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (RouteStop candidate : candidates) {
            double distance = distanceBetweenKm(candidate, target);
            if (Double.isNaN(distance)) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private void scheduleContractBlock(RouteStop stop, @Nullable RouteStop previous, int blockStartMinutes) {
        double distanceKm = previous == null ? 0d : distanceBetweenKm(previous, stop);
        int travelMinutes = previous == null ? 0 : estimateMinutes(distanceKm);
        if (travelMinutes < 0) travelMinutes = 0;
        stop.legDistanceKm = distanceKm;
        stop.estimatedMinutes = travelMinutes;
        stop.plannedStartMinutes = blockStartMinutes;
        stop.plannedEndMinutes = blockStartMinutes + CONTRACT_SERVICE_MINUTES;
    }

    private void scheduleManualJob(RouteStop job, @Nullable RouteStop previous, int currentMinutes) {
        double distanceKm = previous == null ? 0d : distanceBetweenKm(previous, job);
        int travelMinutes = previous == null ? 0 : estimateMinutes(distanceKm);
        if (travelMinutes < 0) travelMinutes = 0;
        job.legDistanceKm = distanceKm;
        job.estimatedMinutes = travelMinutes;
        job.plannedStartMinutes = Math.max(currentMinutes, job.requestedStartMinutes);
        job.plannedEndMinutes = job.requestedEndMinutes > job.plannedStartMinutes
                ? job.requestedEndMinutes
                : job.plannedStartMinutes + 60;
    }

    private void scheduleAnchorReturn(RouteStop stop, @Nullable RouteStop previous, int anchorMinutes) {
        double distanceKm = previous == null ? 0d : distanceBetweenKm(previous, stop);
        int travelMinutes = previous == null ? 0 : estimateMinutes(distanceKm);
        if (travelMinutes < 0) travelMinutes = 0;
        stop.legDistanceKm = distanceKm;
        stop.estimatedMinutes = travelMinutes;
        stop.plannedStartMinutes = anchorMinutes;
        stop.plannedEndMinutes = anchorMinutes;
    }

    private void recalculatePlannedWindows(List<RouteStop> route, RouteOptions options) {
        if (route == null || route.isEmpty()) return;
        int currentMinutes = options.routeStartMinutes;
        RouteStop previous = null;
        for (int i = 0; i < route.size(); i++) {
            RouteStop stop = route.get(i);
            if (stop == null) continue;
            if (i == 0 && stop.routeAnchor) {
                stop.plannedStartMinutes = currentMinutes;
                stop.plannedEndMinutes = currentMinutes;
                previous = stop;
                continue;
            }

            int travelMinutes = previous == null ? 0 : Math.max(0, stop.estimatedMinutes);
            int arrivalMinutes = currentMinutes + travelMinutes;
            if (stop.routeAnchor) {
                stop.plannedStartMinutes = Math.min(Math.max(arrivalMinutes, options.routeEndMinutes), options.routeHardEndMinutes);
                stop.plannedEndMinutes = stop.plannedStartMinutes;
            } else if (stop.manualJob) {
                stop.plannedStartMinutes = Math.max(arrivalMinutes, stop.requestedStartMinutes);
                stop.plannedEndMinutes = stop.requestedEndMinutes > stop.plannedStartMinutes
                        ? stop.requestedEndMinutes
                        : stop.plannedStartMinutes + 60;
            } else {
                stop.plannedStartMinutes = arrivalMinutes;
                stop.plannedEndMinutes = stop.plannedStartMinutes + CONTRACT_SERVICE_MINUTES;
            }
            currentMinutes = stop.plannedEndMinutes;
            previous = stop;
        }
    }

    private double distanceBetweenKm(RouteStop first, RouteStop second) {
        if (first == null || second == null || !first.hasCoordinates() || !second.hasCoordinates()) {
            return Double.NaN;
        }
        double earthRadiusKm = 6371.0d;
        double dLat = Math.toRadians(second.latitude - first.latitude);
        double dLng = Math.toRadians(second.longitude - first.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(first.latitude)) * Math.cos(Math.toRadians(second.latitude))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private int estimateMinutes(double distanceKm) {
        if (Double.isNaN(distanceKm) || distanceKm < 0d) return -1;
        return Math.max(1, (int) Math.round((distanceKm / ESTIMATED_SPEED_KMH) * 60d));
    }

    private int estimateTravelMinutes(@Nullable RouteStop from, RouteStop to) {
        if (from == null) return 0;
        int minutes = estimateMinutes(distanceBetweenKm(from, to));
        return Math.max(0, minutes);
    }

    private String buildPreviewText(String ownerLabel, List<RouteStop> route, File pdfFile, RouteOptions options, boolean usedServerRoute) {
        List<String> lines = new ArrayList<>();
        lines.add("Best route for " + ownerLabel);
        lines.add("Route day: " + routeDayFormat.format(new Date(options.routeDateMillis)));
        lines.add("Saved to: " + pdfFile.getName());
        lines.add("Stops: " + route.size());
        lines.add("Estimated total distance: " + formatDistance(totalDistance(route)));
        lines.add("Estimated drive time: " + formatMinutes(totalMinutes(route)));
        lines.add("Day window: " + formatClock(options.routeStartMinutes) + " - " + formatClock(options.routeHardEndMinutes));
        lines.add("");
        for (int i = 0; i < route.size(); i++) {
            RouteStop stop = route.get(i);
            lines.add((i + 1) + ". " + safeString(stop.name));
            lines.add("   " + safeString(stop.address));
            if (stop.manualJob) {
                lines.add("   Job time: " + formatClock(stop.requestedStartMinutes) + " - " + formatClock(stop.requestedEndMinutes));
            } else if (!stop.routeAnchor) {
                lines.add("   Next visit: " + safeString(stop.nextVisit) + (stop.behind ? " (Behind)" : ""));
            }
            String slotLabel = stop.routeAnchor ? "Route time" : (stop.manualJob ? "Booked slot" : "Suggested slot");
            lines.add("   " + slotLabel + ": " + formatClock(stop.plannedStartMinutes) + " - " + formatClock(stop.plannedEndMinutes));
            if (i > 0) {
                lines.add("   From previous: " + formatDistance(stop.legDistanceKm) + " | " + formatMinutes(stop.estimatedMinutes));
            }
        }
        lines.add("");
        lines.add(usedServerRoute
                ? "Travel distance and drive time use the server Google route service."
                : "Contracts are estimated into 45-minute blocks so fixed jobs stay on time and the route finishes back by the end of the day.");
        if (!routeProviderWarning.isEmpty()) {
            lines.add(routeProviderWarning);
        }
        if (!TextUtils.isEmpty(routeDiagnostics)) {
            lines.add(routeDiagnostics);
        }
        return TextUtils.join("\n", lines);
    }

    private long parseRouteDate(String value) {
        if (TextUtils.isEmpty(value) || "N/A".equalsIgnoreCase(value.trim())) return Long.MAX_VALUE;
        try {
            Date parsed = dateFormat.parse(value.trim());
            return parsed != null ? parsed.getTime() : Long.MAX_VALUE;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private String calculateNextVisit(String lastVisit, Object visitsValue) {
        int visits = 0;
        if (visitsValue instanceof Number) {
            visits = ((Number) visitsValue).intValue();
        } else if (visitsValue != null) {
            try {
                visits = Integer.parseInt(String.valueOf(visitsValue).trim());
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(lastVisit) || "N/A".equalsIgnoreCase(lastVisit) || visits <= 0) return "N/A";
        try {
            Calendar cal = Calendar.getInstance();
            Date parsed = dateFormat.parse(lastVisit);
            if (parsed == null) return "N/A";
            cal.setTime(parsed);
            switch (visits) {
                case 12: cal.add(Calendar.WEEK_OF_YEAR, 4); break;
                case 8: cal.add(Calendar.WEEK_OF_YEAR, 6); break;
                case 6: cal.add(Calendar.WEEK_OF_YEAR, 8); break;
                case 4: cal.add(Calendar.WEEK_OF_YEAR, 12); break;
                default: return "N/A";
            }
            return dateFormat.format(cal.getTime());
        } catch (Exception e) {
            return "N/A";
        }
    }

    private boolean isPastDue(String nextVisit) {
        if (TextUtils.isEmpty(nextVisit) || "N/A".equalsIgnoreCase(nextVisit.trim())) return true;
        try {
            Date date = dateFormat.parse(nextVisit.trim());
            return date == null || date.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isPastDueOnDay(String nextVisit, long routeDateMillis) {
        if (TextUtils.isEmpty(nextVisit) || "N/A".equalsIgnoreCase(nextVisit.trim())) return true;
        try {
            Date date = dateFormat.parse(nextVisit.trim());
            if (date == null) return true;
            Calendar routeDay = Calendar.getInstance();
            routeDay.setTimeInMillis(routeDateMillis);
            routeDay.set(Calendar.HOUR_OF_DAY, 0);
            routeDay.set(Calendar.MINUTE, 0);
            routeDay.set(Calendar.SECOND, 0);
            routeDay.set(Calendar.MILLISECOND, 0);
            return date.before(routeDay.getTime());
        } catch (Exception e) {
            return true;
        }
    }

    @Nullable
    private StaffDirectory.OwnerOption getSelectedOwner() {
        if (ownerOptions.isEmpty()) return null;
        int selectedIndex = ownerSpinner != null ? ownerSpinner.getSelectedItemPosition() : 0;
        if (selectedIndex < 0 || selectedIndex >= ownerOptions.size()) selectedIndex = 0;
        return ownerOptions.get(selectedIndex);
    }

    private void openCreatedRoutes() {
        startActivity(new android.content.Intent(this, CreatedRoutesActivity.class));
    }

    private void openCurrentRouteInGoogleMaps() {
        if (lastGeneratedRoute == null || lastGeneratedRoute.size() < 2 || directionsClient == null) {
            Toast.makeText(this, "Create a route first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = directionsClient.buildGoogleMapsDirectionsUrl(lastGeneratedRoute);
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "This route does not have enough coordinates to open in Google Maps.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent.setPackage(null);
        }
        startActivity(intent);
    }

    private void rememberRouteProviderWarning(Exception error) {
        if (!TextUtils.isEmpty(routeProviderWarning) || error == null) return;
        String message = error.getMessage() != null ? error.getMessage().trim() : "Server route call failed.";
        routeProviderWarning = "Route service warning: " + message;
    }

    private void setBusy(boolean busy) {
        if (createButton != null) createButton.setEnabled(!busy);
        if (viewRoutesButton != null) viewRoutesButton.setEnabled(!busy);
        if (ownerSpinner != null) ownerSpinner.setEnabled(!busy && session != null && session.isAdmin);
    }

    private void setStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    private RouteAnchor geocodeRouteAnchor(String label, String addressText) {
        if (TextUtils.isEmpty(addressText)) return null;
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> matches = geocoder.getFromLocationName(addressText, 1);
            if (matches == null || matches.isEmpty()) return null;
            Address match = matches.get(0);
            return new RouteAnchor(label, addressText, match.getLatitude(), match.getLongitude());
        } catch (Exception ignored) {
            return null;
        }
    }

    private RouteStop createAnchorStop(RouteAnchor anchor, boolean returnStop) {
        String name = returnStop ? "Return to " + anchor.label : anchor.label;
        return new RouteStop("", name, anchor.address, "N/A", false, "", true, false, anchor.latitude, anchor.longitude);
    }

    private List<RouteStop> selectClosestContracts(List<RouteStop> contracts,
                                                   List<RouteStop> jobs,
                                                   @Nullable RouteAnchor anchor,
                                                   int limit) {
        if (contracts == null || contracts.isEmpty() || limit <= 0) return new ArrayList<>();
        if (contracts.size() <= limit) return new ArrayList<>(contracts);

        RouteStop reference = null;
        if (anchor != null) {
            reference = new RouteStop("", anchor.label, anchor.address, "N/A", false, "", true, false, anchor.latitude, anchor.longitude);
        } else if (jobs != null) {
            for (RouteStop job : jobs) {
                if (job != null && job.hasCoordinates()) {
                    reference = job;
                    break;
                }
            }
        }

        final RouteStop referenceStop = (reference != null && reference.hasCoordinates()) ? reference : null;
        List<RouteStop> due = new ArrayList<>();
        List<RouteStop> upcoming = new ArrayList<>();
        for (RouteStop stop : contracts) {
            if (stop != null && stop.behind) {
                due.add(stop);
            } else if (stop != null) {
                upcoming.add(stop);
            }
        }

        java.util.Comparator<RouteStop> dueDateComparator = (left, right) -> {
            long leftDate = parseRouteDate(left.nextVisit);
            long rightDate = parseRouteDate(right.nextVisit);
            int byDate = Long.compare(leftDate, rightDate);
            if (byDate != 0) return byDate;
            if (referenceStop != null) {
                double leftDistance = distanceBetweenKm(referenceStop, left);
                double rightDistance = distanceBetweenKm(referenceStop, right);
                boolean leftValid = !Double.isNaN(leftDistance);
                boolean rightValid = !Double.isNaN(rightDistance);
                if (leftValid != rightValid) return leftValid ? -1 : 1;
                if (leftValid && rightValid) {
                    int byDistance = Double.compare(leftDistance, rightDistance);
                    if (byDistance != 0) return byDistance;
                }
            }
            return safeString(left.name).compareToIgnoreCase(safeString(right.name));
        };

        due.sort(dueDateComparator);
        upcoming.sort(dueDateComparator);

        List<RouteStop> selected = new ArrayList<>();
        for (RouteStop stop : due) {
            if (selected.size() >= limit) break;
            selected.add(stop);
        }
        for (RouteStop stop : upcoming) {
            if (selected.size() >= limit) break;
            selected.add(stop);
        }
        return selected;
    }

    private double totalDistance(List<RouteStop> route) {
        double total = 0d;
        for (RouteStop stop : route) {
            if (stop != null && !Double.isNaN(stop.legDistanceKm)) total += stop.legDistanceKm;
        }
        return total;
    }

    private int totalMinutes(List<RouteStop> route) {
        int total = 0;
        for (RouteStop stop : route) {
            if (stop != null && stop.estimatedMinutes > 0) total += stop.estimatedMinutes;
        }
        return total;
    }

    private String formatDistance(double distanceKm) {
        if (Double.isNaN(distanceKm) || distanceKm < 0d) return "Unavailable";
        return String.format(Locale.getDefault(), "%.1f km", distanceKm);
    }

    private String formatMinutes(int minutes) {
        if (minutes < 0) return "Unavailable";
        if (minutes < 60) return minutes + " min";
        return String.format(Locale.getDefault(), "%dh %02dm", minutes / 60, minutes % 60);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int parseClockToMinutes(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        try {
            String raw = value.trim();
            int hour;
            int minute;

            if (raw.contains(":")) {
                if (!raw.matches("\\d{1,2}:\\d{2}")) return -1;
                String[] parts = raw.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } else {
                if (!raw.matches("\\d{3,4}")) return -1;
                if (raw.length() == 3) {
                    hour = Integer.parseInt(raw.substring(0, 1));
                    minute = Integer.parseInt(raw.substring(1, 3));
                } else {
                    hour = Integer.parseInt(raw.substring(0, 2));
                    minute = Integer.parseInt(raw.substring(2, 4));
                }
            }

            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return (hour * 60) + minute;
        } catch (Exception e) {
            return -1;
        }
    }

    private String formatClock(int minutes) {
        if (minutes < 0) return "Unavailable";
        return String.format(Locale.getDefault(), "%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    private List<RouteStop> cloneManualJobs() {
        List<RouteStop> out = new ArrayList<>();
        for (RouteStop job : manualJobs) {
            RouteStop copy = new RouteStop(job.documentId, job.name, job.address, job.nextVisit, false, "", false, true, job.latitude, job.longitude);
            copy.requestedStartMinutes = job.requestedStartMinutes;
            copy.requestedEndMinutes = job.requestedEndMinutes;
            out.add(copy);
        }
        return out;
    }

    private void showAddJobDialog() {
        if (manualJobs.size() >= MAX_ROUTE_SIZE) {
            Toast.makeText(this, "You already have 10 jobs. Clear some before adding more.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding / 2, padding, 0);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Job name");
        container.addView(nameInput);

        EditText addressInput = new EditText(this);
        addressInput.setHint("Job address or eircode");
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressParams.topMargin = dpToPx(10);
        addressInput.setLayoutParams(addressParams);
        container.addView(addressInput);

        EditText startInput = new EditText(this);
        startInput.setHint("Start time (HH:mm)");
        startInput.setInputType(InputType.TYPE_CLASS_DATETIME);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startParams.topMargin = dpToPx(10);
        startInput.setLayoutParams(startParams);
        container.addView(startInput);

        EditText endInput = new EditText(this);
        endInput.setHint("End time (HH:mm)");
        endInput.setInputType(InputType.TYPE_CLASS_DATETIME);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endParams.topMargin = dpToPx(10);
        endInput.setLayoutParams(endParams);
        container.addView(endInput);

        new AlertDialog.Builder(this)
                .setTitle("Add Day Job")
                .setView(container)
                .setPositiveButton("Add", (dialog, which) -> {
                    String jobName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    String address = addressInput.getText() != null ? addressInput.getText().toString().trim() : "";
                    int startMinutes = parseClockToMinutes(startInput.getText() != null ? startInput.getText().toString().trim() : "");
                    int endMinutes = parseClockToMinutes(endInput.getText() != null ? endInput.getText().toString().trim() : "");
                    if (jobName.isEmpty() || address.isEmpty() || startMinutes < 0 || endMinutes <= startMinutes) {
                        Toast.makeText(this, "Enter job name, address, and a valid time range.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RouteStop job = new RouteStop("", jobName, address, "N/A", false, "", false, true, Double.NaN, Double.NaN);
                    job.requestedStartMinutes = startMinutes;
                    job.requestedEndMinutes = endMinutes;
                    manualJobs.add(job);
                    manualJobs.sort((left, right) -> Integer.compare(left.requestedStartMinutes, right.requestedStartMinutes));
                    refreshJobsSummary();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshJobsSummary() {
        if (jobsSummaryText == null) return;
        if (manualJobs.isEmpty()) {
            jobsSummaryText.setText("No jobs added yet.");
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Jobs added: " + manualJobs.size() + " | Contracts available: " + Math.max(0, MAX_ROUTE_SIZE - manualJobs.size()));
        for (int i = 0; i < manualJobs.size(); i++) {
            RouteStop job = manualJobs.get(i);
            lines.add((i + 1) + ". " + safeString(job.name) + " (" + formatClock(job.requestedStartMinutes) + " - " + formatClock(job.requestedEndMinutes) + ")");
            lines.add("   " + safeString(job.address));
        }
        jobsSummaryText.setText(TextUtils.join("\n", lines));
    }

    private static String safeString(Object... values) {
        for (Object value : values) {
            if (value == null) continue;
            String string = String.valueOf(value).trim();
            if (!string.isEmpty()) return string;
        }
        return "";
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value != null ? Double.parseDouble(String.valueOf(value).trim()) : Double.NaN;
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static final class RouteOptions {
        int startMode = START_OPTION_NO_START;
        String homeAddress = "";
        int routeStartMinutes = -1;
        int routeEndMinutes = DAY_END_SOFT_MINUTES;
        int routeHardEndMinutes = DAY_END_HARD_MINUTES;
        long routeDateMillis = System.currentTimeMillis();
        boolean returnToStart = false;
    }

    private static final class RouteAnchor {
        final String label;
        final String address;
        final double latitude;
        final double longitude;

        RouteAnchor(String label, String address, double latitude, double longitude) {
            this.label = label != null ? label : "Start";
            this.address = address != null ? address : "Start location";
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
