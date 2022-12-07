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
package kr.apptimer.android.activity.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.rxjava3.schedulers.Schedulers;
import javax.inject.Inject;
import kr.apptimer.R;
import kr.apptimer.android.activity.main.recycler.AppViewAdapter;
import kr.apptimer.android.activity.main.recycler.AppViewHolder;
import kr.apptimer.base.InjectedAppCompatActivity;
import kr.apptimer.dagger.android.IntentCache;
import kr.apptimer.dagger.context.ActivityContext;
import kr.apptimer.database.LocalDatabase;
import kr.apptimer.database.data.InstalledApplication;

public class PermissionPage extends InjectedAppCompatActivity {
    private static final int SPAN_COUNT = 5;

    @Inject
    LocalDatabase database;

    @Inject
    IntentCache cache;

    /***
     * Called after calling {@link ActivityContext#inject(any extends InjectedAppCompatActivity)} in context of {@link #onCreate(Bundle)}
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreate(@Nullable Bundle savedInstanceState) {
        SharedPreferences pref = getSharedPreferences("isFirst", Activity.MODE_PRIVATE);
        boolean first = pref.getBoolean("isFirst", false);
        if (!first) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putBoolean("isFirst", true);
            editor.commit();
            setContentView(R.layout.activity_permission_request);
            Button CheckButton = findViewById(R.id.check);
            CheckButton.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), Slider.class);
                startActivity(intent);
            });
        } else {
            setContentView(R.layout.activity_main);
            Button StaticButton = findViewById(R.id.statistics);
            StaticButton.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), StatisticsPage.class);
                startActivity(intent);
            });
            Button cancelReservationButton = findViewById(R.id.reservationNo);
            cancelReservationButton.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), ReservationCancelPage.class);
                startActivity(intent);
            });

            RecyclerView recyclerView = findViewById(R.id.app);
            recyclerView.setLayoutManager(new GridLayoutManager(getApplicationContext(), SPAN_COUNT));

            AppViewAdapter appViewAdapter = new AppViewAdapter(
                    database.installedApplicationDao(), getApplicationContext().getPackageManager());

            recyclerView.setAdapter(appViewAdapter);

            Button cancelButton = findViewById(R.id.reservationNo);

            cancelButton.setOnClickListener(view -> {
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
                    if (viewHolder instanceof AppViewHolder) {
                        AppViewHolder app = (AppViewHolder) viewHolder;

                        if (app.isSelected()) {
                            AlertDialog dialog = new AlertDialog.Builder(this)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setTitle("알림")
                                    .setMessage("삭제 예정을 취소하시겠어요?")
                                    .setPositiveButton("예", (dialogInterface, i1) -> {
                                        database.installedApplicationDao()
                                                .findByPackageUri(app.getPackageUri())
                                                .observeOn(Schedulers.io())
                                                .subscribe(application -> {
                                                    cancel(application);

                                                    database.installedApplicationDao()
                                                            .delete(application);

                                                    appViewAdapter.reload();

                                                    Toast toast = Toast.makeText(
                                                            PermissionPage.this, "예약이 취소되었습니다.", Toast.LENGTH_SHORT);
                                                    toast.show();
                                                });
                                    })
                                    .setNegativeButton("아니요", null)
                                    .create();

                            dialog.show();
                        }
                    }
                }
            });
        }
    }

    private void cancel(InstalledApplication installedApplication) {
        PendingIntent pendingIntent = cache.getCachedIntent(installedApplication.getPackageUri());

        if (pendingIntent == null) return;

        pendingIntent.cancel();
    }
    /***
     * Fill the method body to inject subclass of this using {@param context}
     * @param context {@link ActivityContext}
     */
    @Override
    protected void inject(ActivityContext context) {
        context.inject(this);
    }
}
