package rent.auto;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.vk.sdk.VKSdk;

import butterknife.BindView;
import butterknife.ButterKnife;
import rent.auto.authorization.LoginActivity;
import rent.auto.util.Preferences;

public class SplashActivity extends AppCompatActivity {

    @BindView(R.id.no_network_view)
    TextView mNoNetwork;
    @BindView(R.id.try_again_button)
    TextView mTryAgain;
    @BindView(R.id.progress_bar)
    ProgressBar mProgressBar;
    FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        ButterKnife.bind(this);


        manageOnline();


    }


    private void manageOnline() {
        if (!isOnline()) {
            mNoNetwork.setVisibility(View.VISIBLE);
            mTryAgain.setVisibility(View.VISIBLE);
            mTryAgain.setOnClickListener(v -> {
                mProgressBar.setVisibility(View.VISIBLE);
                new Handler().postDelayed(() -> {
                    mProgressBar.setVisibility(View.GONE);
                    manageOnline();
                }, 2000);
            });
        } else {
            mNoNetwork.setVisibility(View.INVISIBLE);
            mTryAgain.setVisibility(View.INVISIBLE);

            mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
            mFirebaseRemoteConfig.setDefaults(R.xml.remote_config);
            FirebaseAnalytics.getInstance(this).setUserProperty("language", App.get().getARLocale());

            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setDeveloperModeEnabled(BuildConfig.DEBUG)
                    .build();
            mFirebaseRemoteConfig.setConfigSettings(configSettings);
            mFirebaseRemoteConfig.fetch(60 * 60 * 24).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    mFirebaseRemoteConfig.activateFetched();
                    if (mFirebaseRemoteConfig.getBoolean("update_required")) {
                        Dialog dialog = new AlertDialog.Builder(SplashActivity.this)
                                .setTitle(mFirebaseRemoteConfig.getString("update_alert_title"))
                                .setMessage(mFirebaseRemoteConfig.getString("update_alert_text"))
                                .setCancelable(false)
                                .setPositiveButton(mFirebaseRemoteConfig.getString("update_alert_ok"), (dialog12, which) -> {
                                    final String appPackageName = getPackageName();
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                                    } catch (android.content.ActivityNotFoundException anfe) {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                                    }
                                    SplashActivity.this.finish();
                                })
                                .setNegativeButton(mFirebaseRemoteConfig.getString("update_alert_cancel"), (dialog1, which) -> SplashActivity.this.finish())
                                .create();
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.show();

                    } else {
                        doStart();
                    }


                } else {
                    doStart();
                }


            });


        }

    }

    private void doStart() {
        Intent intent;


        if (TextUtils.isEmpty(Preferences.getSession())) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail().requestIdToken(getString(R.string.server_client_id))
                        .build();


                GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);
                signInClient.signOut();
            }

            if (VKSdk.isLoggedIn()) {
                VKSdk.logout();
            }

            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            if (accessToken != null) {
                LoginManager.getInstance().logOut();
            }


            intent = new Intent(SplashActivity.this, LoginActivity.class);


        } else {


            intent = new Intent(SplashActivity.this, MainActivity.class);

        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    public boolean isOnline() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connMgr != null) {
            networkInfo = connMgr.getActiveNetworkInfo();
        }
        return (networkInfo != null && networkInfo.isConnected());
    }
}
