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
package kr.apptimer.dagger.android.task;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import kr.apptimer.base.InjectApplicationContext;
import kr.apptimer.dagger.android.ApplicationRemovalExecutor;
import kr.apptimer.dagger.android.NotificationHelper;
import kr.apptimer.database.LocalDatabase;
import kr.apptimer.database.data.InstalledApplication;
import kr.apptimer.database.data.InstalledApplicationParcelable;

/***
 * Executes a task reserved at a time by
 * {@link android.app.AlarmManager#setAndAllowWhileIdle(int, long, PendingIntent)}
 *
 * @author Singlerr
 */
public final class TaskExecutor extends BroadcastReceiver {

    public static final String EXECUTOR_PACKAGE_URI = "package_uri";

    public static final String EXECUTOR_NAME = "name";

    @Inject
    LocalDatabase database;

    @Inject
    NotificationHelper notificationHelper;

    @Inject
    ApplicationRemovalExecutor removalExecutor;

    public TaskExecutor() {
        super();
        InjectApplicationContext.getInstance().getContext().inject(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.hasExtra(EXECUTOR_NAME) && intent.hasExtra(EXECUTOR_PACKAGE_URI)){

            String packageUri = intent.getStringExtra(EXECUTOR_PACKAGE_URI);



            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    removalExecutor.requestRemoval(packageUri);
                }
            });

            notificationHelper.sendNotification("앱 예약 삭제", intent.getStringExtra(EXECUTOR_NAME) + "이(가) 삭제되었습니다.");
            Log.d("taskExecutor", "removed");

            database.installedApplicationDao().deleteByPackageUri(packageUri);

        }
    }
}
