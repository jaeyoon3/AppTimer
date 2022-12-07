/*
Copyright 2022 singlerr

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of singlerr nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package kr.apptimer.android.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import java.util.Calendar;
import javax.inject.Inject;
import kr.apptimer.R;
import kr.apptimer.base.InjectApplicationContext;
import kr.apptimer.dagger.android.AppAnalyticsHandler;
import kr.apptimer.dagger.android.ApplicationRemovalExecutor;
import kr.apptimer.dagger.android.OverlayViewModel;
import kr.apptimer.dagger.android.TaskScheduler;
import kr.apptimer.dagger.android.task.SerializableTask;
import kr.apptimer.database.LocalDatabase;
import kr.apptimer.database.data.ApplicationStats;
import kr.apptimer.database.data.InstalledApplication;

/***
 * Android service class for showing setting overlay In the overlay, user can
 * set how long an installed application will last.
 *
 * @author Singlerr
 */
public final class AppExpirationOverlayService extends Service {

    @Inject
    AppAnalyticsHandler analyticsHandler;

    @Inject
    OverlayViewModel viewModel;

    @Inject
    TaskScheduler taskScheduler;

    @Inject
    ApplicationRemovalExecutor removalExecutor;

    @Inject
    LocalDatabase database;

    private WindowManager windowManager;

    private View view;

    private Button buttonPositive;
    private Button buttonNegative;

    private EditText editDay;
    private EditText editHour;
    private EditText editMinute;

    private String packageName;

    @Override
    public void onCreate() {
        super.onCreate();

        InjectApplicationContext.getInstance().getContext().inject(this);

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        view = inflater.inflate(viewModel.getLayoutId(), null);

        windowManager.addView(view, viewModel.getLayoutParams());
        // View 연결
        buttonPositive = view.findViewById(R.id.ok);
        buttonNegative = view.findViewById(R.id.cancel);
        editDay = view.findViewById(R.id.day);
        editHour = view.findViewById(R.id.hour);
        editMinute = view.findViewById(R.id.minute);

        buttonNegative.setOnClickListener(
                view -> stopService(new Intent(getApplicationContext(), AppExpirationOverlayService.class)));
        buttonPositive.setOnClickListener(view -> {
            try {
                int day;
                int hour;
                int second;

                if (validateTime(
                        (day = Integer.parseInt(editDay.getText().toString())),
                        (hour = Integer.parseInt(editHour.getText().toString())),
                        (second = Integer.parseInt(editMinute.getText().toString())))) {

                    Calendar calendar = Calendar.getInstance();

                    calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) - 1, day, hour, second);

                    String applicationName = getPackageManager().getApplicationInfo(packageName, 0).name;

                    database.installedApplicationDao()
                            .insert(new InstalledApplication(packageName, applicationName, calendar.getTime()));

                    taskScheduler.scheduleTask(
                            packageName,
                            (SerializableTask) () -> removalExecutor.requestRemoval(packageName),
                            calendar.getTime());
                    Toast.makeText(getApplicationContext(), "예약되었습니다.", Toast.LENGTH_SHORT)
                            .show();
                    windowManager.removeView(view);
                } else
                    Toast.makeText(getApplicationContext(), "비정상적인 수치를 입력했습니다.", Toast.LENGTH_SHORT)
                            .show();
            } catch (NullPointerException e) {
                Toast.makeText(getApplicationContext(), "빈 칸이 있습니다.", Toast.LENGTH_SHORT)
                        .show();
                Log.d(e.toString(), e.getMessage());
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(e.toString(), e.getMessage());
            } catch (NumberFormatException ex) {
                Toast.makeText(getApplicationContext(), "빈 칸이 있습니다.", Toast.LENGTH_SHORT)
                        .show();
            }
        });
        editDay.setOnClickListener(view -> {
            InputMethodManager imm =
                    (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, 0);
        });

        editDay.setFocusableInTouchMode(true);
        editDay.requestFocus();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        packageName = intent.getStringExtra("pkgName");
        ImageView appIcon = view.findViewById(R.id.appicon);

        TextView statsView = view.findViewById(R.id.stat_description);

        analyticsHandler.getAppInformation(
                packageName,
                applicationStats -> {
                    ApplicationStats.DueCategory dueCategory = applicationStats.getMostCommon();
                    if (dueCategory == null) statsView.setText("");
                    statsView.setText(formatText(dueCategory));
                },
                e -> statsView.setText(""));

        try {
            Drawable icon = getPackageManager().getApplicationIcon(packageName);
            appIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(getClass().getName(), "Cannot load application icon");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private boolean validateTime(int day, int hour, int minute) {
        return day >= 0 && hour >= 0 && hour <= 24 && minute >= 0 && minute <= 60;
    }

    private String formatText(ApplicationStats.DueCategory dueCategory) {
        String format = "이 앱은 보통 %s 삭제돼요.";
        switch (dueCategory) {
            case SHORT:
                return String.format(format, "30분 이내에");
            case MEDIUM:
                return String.format(format, "1시간 이내에");
            case LONG:
                return String.format(format, "2시간 넘어서");
            default:
                return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager == null || view == null) return;

        windowManager.removeView(view);
        windowManager = null;
        view = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
