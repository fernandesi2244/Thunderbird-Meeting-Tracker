package com.gmail.fernandesi2244.thunderbirdmeetingtracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.parse.FindCallback;
import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MeetingSignInActivity extends AppCompatActivity {

    public static final String MeetingSignInActivityID = "MeetingSignInActivity";
    public static final int MILLISECONDS_PER_MINUTE = 60_000;

    private static long marginInMinutes;

    private FusedLocationProviderClient mFusedLocationProviderClient;
    private Location currentLoc;
    private ParseObject clickedMeeting;
    private float locationMargin;
    private boolean userLate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meeting_sign_in);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        setUpMeetingsList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.base_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; goto parent activity.
                this.finish();
                return true;
            case R.id.menu_refresh:
                refreshScreen();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setUpMeetingsList() {
        LinearLayout meetingsLayout = findViewById(R.id.meetingsLayout);
        meetingsLayout.removeAllViews();

        long marginInMilliseconds = 24 * 60 * MILLISECONDS_PER_MINUTE; //milliseconds in a day
        Date earliestDate = new Date();
        earliestDate.setTime(new Date().getTime() - marginInMilliseconds);

        ParseUser currentUser = ParseUser.getCurrentUser();
        try {
            currentUser = ParseUser.getCurrentUser();
            if(currentUser==null)
                throw new Exception();
        } catch(Exception e) {
            //Perhaps the user is zooming through the app without giving the app enough time to load.
            Toast.makeText(getApplicationContext(), "Something wrong happened... Please try again later!", Toast.LENGTH_LONG).show();
            goToProfile();
        }
        String[] acceptableDepartments = {"General", "general", currentUser.getString("department")};

        ParseQuery<ParseObject> findEligibleMeetings = ParseQuery.getQuery("Meeting");
        findEligibleMeetings.whereContainedIn("audience", Arrays.asList(acceptableDepartments));
        findEligibleMeetings.whereGreaterThanOrEqualTo("meetingDate", earliestDate);
        findEligibleMeetings.orderByDescending("meetingDate");

        try {
            List<ParseObject> meetings = findEligibleMeetings.find();
            if (meetings.size() == 0)
                throw new Exception("failed");

            for (ParseObject current : meetings) {
                Button nextButton = new Button(this);
                nextButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                nextButton.setGravity(Gravity.LEFT);

                String meetingDescription = current.getString("meetingDescription");
                Date nextMeetingDate = current.getDate("meetingDate");
                DateFormat df = new SimpleDateFormat("M/dd/yy @ h:mm a");
                String date = df.format(nextMeetingDate);
                String html = "<b>Description:</b> " + meetingDescription + "<br/><b>Time:</b> " + date + "<br/><b>ID:</b> " + current.getObjectId();
                Spanned durationSpanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
                nextButton.setText(durationSpanned);
                nextButton.setId(View.generateViewId());
                nextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Button clickedButton = (Button) v;
                        String btnText = clickedButton.getText().toString();
                        String objectId = btnText.split("ID: ")[1];

                        ParseQuery<ParseObject> getMeeting = ParseQuery.getQuery("Meeting");
                        getMeeting.whereEqualTo("objectId", objectId);
                        getMeeting.findInBackground(new FindCallback<ParseObject>() {
                            @Override
                            public void done(List<ParseObject> objects, ParseException e) {
                                if (e == null) {
                                    if (objects.size() == 1) {
                                        clickedMeeting = objects.get(0);
                                        verifyMeetingAttendance();
                                    } else {
                                        Toast.makeText(getApplicationContext(), "Something went wrong when accessing the desired meeting. Please try again!", Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    Toast.makeText(getApplicationContext(), "Something went wrong when accessing the desired meeting. Please try again!", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });

                meetingsLayout.addView(nextButton);

            }

        } catch (Exception e) {
            TextView noMeetingsTextView = new TextView(this);
            noMeetingsTextView.setGravity(Gravity.CENTER);
            noMeetingsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            noMeetingsTextView.setText(R.string.noMeetingsScheduledMessage);
            noMeetingsTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            noMeetingsTextView.setId(View.generateViewId());
            meetingsLayout.addView(noMeetingsTextView);
        }
    }

    protected void checkPermissions() {
        if (!hasLocationPermissions()) {
            Intent goToRequestPage = new Intent(this, RequestPermissionsActivity.class);
            goToRequestPage.putExtra("sender", MeetingSignInActivityID);
            startActivity(goToRequestPage);
        }
    }

    private boolean hasLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //All good; user may proceed
            return true;
        } else {
            //User needs to be redirected to permissions activity
            return false;
        }
    }

    private void getDeviceLocation() {
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            Task locationResult = mFusedLocationProviderClient.getLastLocation();
            locationResult.addOnCompleteListener(this, new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull Task task) {
                    if (task.isSuccessful()) {
                        currentLoc = (Location) task.getResult();
                    }
                    resumeVerificationProcessForDeviceLocation();
                }
            });
        } catch (
                SecurityException e) {
            return;
        }
    }

    private void verifyMeetingAttendance() {
        boolean isRemote = clickedMeeting.getBoolean("isRemote");

        ParseQuery<ParseObject> getMargin = ParseQuery.getQuery("MeetingAttendanceMarginOfError");
        getMargin.whereEqualTo("objectId", "ZCHh4cadL1");
        try {
            List<ParseObject> parseObjects = getMargin.find();
            if (parseObjects.size() > 0) {
                marginInMinutes = parseObjects.get(0).getNumber("marginInMinutes").longValue();
            } else {
                Toast.makeText(getApplicationContext(), "Error: Meeting attendance time margin retrieval failed. Please try again later.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (ParseException e) {
            Toast.makeText(getApplicationContext(), "Error: Meeting attendance time margin retrieval failed. Please try again later.", Toast.LENGTH_LONG).show();
            return;
        }

        long marginInMilliseconds = marginInMinutes * MILLISECONDS_PER_MINUTE;
        Date meetingDate = clickedMeeting.getDate("meetingDate");

        Date currentDate = new Date();
        Date beforeDate = new Date();
        beforeDate.setTime(meetingDate.getTime() - marginInMilliseconds);
        Date afterDate = new Date();
        afterDate.setTime(meetingDate.getTime() + marginInMilliseconds);

        boolean timingIsGood = false;

        if (currentDate.getTime() >= beforeDate.getTime()) {
            timingIsGood = true;
        }

        userLate = currentDate.getTime() > afterDate.getTime();

        ParseUser currentUser = ParseUser.getCurrentUser();

        if (isRemote) {
            if (timingIsGood) {
                ArrayList<ParseObject> meetingsAttendedByUser = (ArrayList<ParseObject>) currentUser.get("meetingsAttended");

                if (meetingsAttendedByUser == null) {
                    Toast.makeText(getApplicationContext(), "Something went wrong during online retrieval of data. Please try again later!", Toast.LENGTH_LONG).show();
                    return;
                }

                //Check if user already signed into the meeting
                for (ParseObject mtng : meetingsAttendedByUser) {
                    if (mtng.getObjectId().equals(clickedMeeting.getObjectId())) {
                        Toast.makeText(getApplicationContext(), "You already signed into this meeting!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                meetingsAttendedByUser.add(clickedMeeting);

                currentUser.put("meetingsAttended", meetingsAttendedByUser);
                currentUser.put("noMeetingsAttended", currentUser.getLong("noMeetingsAttended") + 1);

                currentUser.saveEventually();

                ArrayList<ParseUser> meetingUsers = (ArrayList<ParseUser>) clickedMeeting.get("usersThatAttended");
                meetingUsers.add(currentUser);
                clickedMeeting.put("usersThatAttended", meetingUsers);

                if (userLate) {
                    ArrayList<ParseUser> lateMeetingUsers = (ArrayList<ParseUser>) clickedMeeting.get("usersThatAttendedLate");
                    lateMeetingUsers.add(currentUser);
                    clickedMeeting.put("usersThatAttendedLate", lateMeetingUsers);
                }

                clickedMeeting.saveEventually();

                if (userLate)
                    Toast.makeText(getApplicationContext(), "You successfully signed into the meeting late!", Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(getApplicationContext(), "You successfully signed into the meeting on time!", Toast.LENGTH_LONG).show();
                goToProfile();
            } else {
                Toast.makeText(getApplicationContext(), "Sorry, but you are too early to check into the meeting. Please contact an administrator if this is a concern.", Toast.LENGTH_LONG).show();
            }
        } else {
            if (!hasLocationPermissions()) {
                checkPermissions();
            } else {
                if (timingIsGood) {
                    getDeviceLocation();
                } else {
                    Toast.makeText(getApplicationContext(), "Sorry, but you are too early to check into the meeting. Please contact an administrator if this is a concern.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void resumeVerificationProcessForDeviceLocation() {
        if (currentLoc == null) {
            Toast.makeText(getApplicationContext(), "Could not retrieve phone's location. Please try again!", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ParseGeoPoint meetingLocInParse = clickedMeeting.getParseGeoPoint("meetingLocation");
            Location meetingLoc = new Location("");
            meetingLoc.setLatitude(meetingLocInParse.getLatitude());
            meetingLoc.setLongitude(meetingLocInParse.getLongitude());

            ParseQuery<ParseObject> getMargin = ParseQuery.getQuery("LocationMargin");
            getMargin.whereEqualTo("objectId", "s5qyTqdbHY");
            try {
                List<ParseObject> parseObjects = getMargin.find();
                if (parseObjects.size() > 0) {
                    locationMargin = parseObjects.get(0).getNumber("locationMarginInMeters").floatValue();
                } else {
                    Toast.makeText(getApplicationContext(), "Error: Meeting location margin retrieval failed. Please try again later.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (ParseException e) {
                Toast.makeText(getApplicationContext(), "Error: Meeting location margin retrieval failed. Please try again later.", Toast.LENGTH_LONG).show();
                return;
            }

            ParseUser currentUser = ParseUser.getCurrentUser();

            float actualDistance = currentLoc.distanceTo(meetingLoc);
            if (actualDistance < locationMargin) {
                ArrayList<ParseObject> meetingsAttendedByUser = (ArrayList<ParseObject>) currentUser.get("meetingsAttended");

                if (meetingsAttendedByUser == null) {
                    Toast.makeText(getApplicationContext(), "Something went wrong during online retrieval of data. Please try again later!", Toast.LENGTH_LONG).show();
                    return;
                }

                //Check if user already signed into the meeting
                for (ParseObject mtng : meetingsAttendedByUser) {
                    if (mtng.getObjectId().equals(clickedMeeting.getObjectId())) {
                        Toast.makeText(getApplicationContext(), "You already signed into this meeting!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                meetingsAttendedByUser.add(clickedMeeting);

                currentUser.put("meetingsAttended", meetingsAttendedByUser);
                currentUser.put("noMeetingsAttended", currentUser.getLong("noMeetingsAttended") + 1);

                currentUser.saveEventually();

                ArrayList<ParseUser> meetingUsers = (ArrayList<ParseUser>) clickedMeeting.get("usersThatAttended");
                meetingUsers.add(currentUser);
                clickedMeeting.put("usersThatAttended", meetingUsers);

                if (userLate) {
                    ArrayList<ParseUser> lateMeetingUsers = (ArrayList<ParseUser>) clickedMeeting.get("usersThatAttendedLate");
                    lateMeetingUsers.add(currentUser);
                    clickedMeeting.put("usersThatAttendedLate", lateMeetingUsers);
                }

                clickedMeeting.saveEventually();

                if (userLate)
                    Toast.makeText(getApplicationContext(), "You successfully signed into the meeting late!", Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(getApplicationContext(), "You successfully signed into the meeting on time!", Toast.LENGTH_LONG).show();
                goToProfile();
            } else {
                Toast.makeText(getApplicationContext(), "Sorry, but you are not close enough to the meeting location to confirm your presence. Please contact an administrator if this is a concern.", Toast.LENGTH_LONG).show();
            }
        } catch(Exception loc_e) {
            Toast.makeText(getApplicationContext(), "Something went wrong while verifying your location. Please try again later or contact an administrator if this is a concern.", Toast.LENGTH_LONG).show();
        }
    }

    private void goToProfile() {
        Intent goToProfile = new Intent(this, ProfileActivity.class);
        startActivity(goToProfile);
    }

    private void refreshScreen() {
        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }
}
