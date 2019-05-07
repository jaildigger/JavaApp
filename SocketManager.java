package rent.auto.socket;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Looper;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.client.SocketIOException;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;
import io.socket.engineio.client.transports.WebSocket;
import okhttp3.OkHttpClient;
import rent.auto.App;
import rent.auto.BuildConfig;
import rent.auto.NetworkListener;
import rent.auto.model.NewCar;
import rent.auto.model.constant.NotificationStatus;
import rent.auto.model.constant.SocialType;
import rent.auto.util.Preferences;

/**
 * Created by jaydee on 19.02.18.
 */

public class SocketManager {

    private static final String EMAIL = "email";
    private static final String PASSWORD = "password";
    private static final String BOOKING_ID = "booking_id";
    private static final String CAR_ID = "car_id";
    private static final String TAG = "SocketManager";
    private static final String DOCUMENT_TYPE = "document_type";
    @SuppressLint("StaticFieldLeak")
    private static SocketManager instance;
    private Socket mSocket;
    private static final String VERSION_NUMBER = "2.1";
    private NetworkListener networkListener;
    private Emitter.Listener onConnectError = args -> {
        if (args[0] instanceof EngineIOException) {
            EngineIOException exception = (EngineIOException) args[0];
            exception.printStackTrace();
            Crashlytics.logException(exception);
            if (networkListener != null) {
                Looper.prepare();
                networkListener.onNoConnection();
                Looper.loop();
            }
        } else if (args[0] instanceof SocketIOException) {
            SocketIOException exception = (SocketIOException) args[0];
            exception.printStackTrace();
            Crashlytics.logException(exception);
        }
        Log.d(TAG, "error " + args[0].toString());

    };
    private Emitter.Listener onConnect = args -> {
        Log.d(TAG, "connected " + mSocket.connected());
        restoreSession();
    };

    private Emitter.Listener onError = args -> {
        EngineIOException exception = (EngineIOException) args[0];
        exception.printStackTrace();
        Log.d(TAG, "error " + exception.getMessage());

    };
    private Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "disconnected " + mSocket.connected());

    };

    private Emitter.Listener onReconnect = args -> {
        Log.d(TAG, ": reconnected");
    };

    public SocketManager() {
        instance = this;
    }

    public static SocketManager get() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }


    public Socket getSocket() {
        return mSocket;
    }

    public void setNetworkListener(NetworkListener networkListener) {
        this.networkListener = networkListener;
    }

    public void init() {
        try {

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .hostnameVerifier((hostname, session) -> true)
                    .build();


            IO.Options options = new IO.Options();
            IO.setDefaultOkHttpCallFactory(okHttpClient);
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient);
            options.forceNew = true;
            options.timeout = 10000;
            options.reconnection = true;
            options.reconnectionAttempts = 99999;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 5000;
            options.transports = new String[]{WebSocket.NAME};
            options.secure = true;
            mSocket = IO.socket(BuildConfig.SERVER_URL, options);


            mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            mSocket.on(Socket.EVENT_CONNECT, onConnect);
            mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            mSocket.on(Socket.EVENT_ERROR, onError);
            mSocket.on(Socket.EVENT_RECONNECT, onReconnect);

            mSocket.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


    }


    private void run(Api apiName, String params, SocketCallback socketCallback) {
        try {
            run(apiName, new JSONObject(params), true, socketCallback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void addOnConnectListener(Emitter.Listener listener) {
        mSocket.off(Socket.EVENT_CONNECT);
        mSocket.once(Socket.EVENT_CONNECT, listener);
    }

    private void run(Api apiName, JSONObject params, boolean once, SocketCallback socketCallback) {

        if (!mSocket.connected()) {
//            mSocket.io().off();
//            mSocket.io().open(err -> {
//                if (err != null) Log.d(TAG, err.getMessage());
//                else Log.d(TAG, "connected from run");
//            });
            socketCallback.onNoConnection();
            mSocket.connect();
//            return;
        }
        Log.d(TAG, "run " + apiName.getValue());
        Emitter.Listener listener = args -> {
            socketCallback.onResponse(apiName);
            Log.d(TAG, args[0].toString());

            if (args[0] instanceof EngineIOException) {
                EngineIOException e = (EngineIOException) args[0];
                socketCallback.onFail(apiName, e);
            } else {

                if (apiName == Api.NEW_NOTIFICATION) {
                    Log.d(TAG, "NEW_NOTIFICATION " + args[0].toString());
                    socketCallback.onSuccess(apiName, args[0].toString());
                    return;
                } else if (apiName == Api.NEW_BOOKING_MESSAGE) {
                    Log.d(TAG, "NEW_BOOKING_MESSAGE " + args[0].toString());
                    socketCallback.onSuccess(apiName, args[0].toString());
                    return;
                }
                Response result;
                try {
                    result = App.get().getGson().fromJson(args[0].toString(), Response.class);
                } catch (IllegalStateException e) {
                    socketCallback.onError(apiName, new Error(999, e.getMessage()));
                    return;
                }

                if (result.error != null) {
                    Error error = result.error;
                    socketCallback.onError(apiName, error);
                } else if (result.data != null && !result.data.isJsonNull()) {
                    if (result.data.isJsonObject()) {
                        JsonObject object = result.data.getAsJsonObject();
                        socketCallback.onSuccess(apiName, object.toString());
                        Log.d(TAG, "JSONOBJ " + object);
                    } else {
                        JsonArray object = result.data.getAsJsonArray();
                        socketCallback.onSuccess(apiName, object.toString());
                        Log.d(TAG, "JSONARR " + object);
                    }
                }
            }
        };


        if (once) {
            mSocket.once(apiName.getValue(), listener);
        } else {
            mSocket.on(apiName.getValue(), listener);
        }

        if (params == null) {
            params = new JSONObject();

        }
        try {
            params.put("v", VERSION_NUMBER);
            params.put("locale", App.get().getARLocale());
            if (apiName != Api.SET_CAR_PRICE && apiName != Api.ADD_CAR && apiName != Api.GET_PARAMETER_LIST)
                params.put("currency", Preferences.getCurrencyCode());
            params.put("platform", "android");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, apiName.getValue() + ": " + params.toString());


        mSocket.emit(apiName.getValue(), params);


    }


    public void auth(String email, String password, SocketCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put(EMAIL, email);
            params.put(PASSWORD, password);
            run(Api.AUTH, params, true, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    public void socialLogin(String accessToken, SocialType type, String email, SocketCallback callback) {

        JSONObject params = new JSONObject();
        try {
            Log.d(TAG, accessToken);
            params.put("access_token", accessToken);
            params.put("type", type.getValue());
            params.put("aud", "android");
            params.put(EMAIL, email);
            run(Api.SOCIAL_LOGIN, params, true, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void register(String email, String password, String fio, SocketCallback callback) {

        JSONObject params = new JSONObject();
        try {
            params.put(EMAIL, email);
            params.put(PASSWORD, password);
            params.put("fio", fio);
            run(Api.REGISTER, params, true, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void restoreSession() {

        if (!Preferences.getSession().isEmpty()) {
            run(Api.RESTORE_SESSION, getParamsWithSession(), true, new EmptySocketCallback());
        }

    }

    public void locationsSortedGet(SocketCallback callback) {
        run(Api.GET_LOCATIONS_SORTED, getParamsWithSession(), true, callback);
    }

    public void carsSearch(SearchRequest searchParams, SocketCallback callback) {
        String params = App.get().getGson().toJson(searchParams);
        Log.d(TAG, params);
        run(Api.SEARCH_CARS, params, callback);
    }

    public void carGet(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.GET_CAR, carIdRequest.convert(), callback);
    }

    public void carBook(BookRequest bookRequest, Activity activity, SocketCallback callback) {
        run(Api.BOOKING, bookRequest.convert(), callback);
    }

    public void userSet(UpdateUserRequest request, SocketCallback callback) {
        run(Api.SET_USER_SETTINGS, request.convert(), callback);
    }

    public void userRise(String phone, Integer locationId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put("phone", phone);
            params.put("location_id", locationId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.USER_RISE, params, true, callback);

    }

    public void bookingGet(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, bookingId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.GET_MY_BOOKING, params, true, callback);
    }

    public void bookingOwnerGet(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, bookingId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.GET_BOOKING_DATA, params, true, callback);
    }

    public void bookingListGet(SocketCallback callback) {
        run(Api.GET_BOOKING_LIST, getParamsWithSession(), true, callback);
    }

    public void docsBookingGet(Integer bookingId, SocketCallback socketCallback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, String.valueOf(bookingId));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.GET_BOOKING_DOCS, params, true, socketCallback);
    }


    public void docsUserGet(SocketCallback socketCallback) {
        JSONObject params = getParamsWithSession();

        run(Api.GET_USER_DOCS, params, true, socketCallback);
    }

    public void docBookingGet(String documentType, Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(DOCUMENT_TYPE, documentType);
            params.put(BOOKING_ID, String.valueOf(bookingId));

        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.GET_BOOKING_DOCUMENT, params, true, callback);
    }

    public void docUserGet(String documentType, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(DOCUMENT_TYPE, documentType);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.GET_USER_DOCUMENT, params, true, callback);
    }


    public void docUserDrop(String docType, SocketCallback callback) {
        JSONObject prams = getParamsWithSession();
        try {
            prams.put(DOCUMENT_TYPE, docType);
        } catch (JSONException e) {
            e.printStackTrace();

        }

        run(Api.DROP_USER_DOCUMENT, prams, true, callback);
    }

    public void docRequest(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, String.valueOf(bookingId));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.REQUEST_DOCUMENT_LIST, params, true, callback);
    }

    public void docUserSet(DocRequest request, SocketCallback callback) {
        run(Api.SET_USER_DOCUMENT, request.convert(), callback);
    }

    public void docsSend(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, String.valueOf(bookingId));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.SEND_DOCUMENT_LIST, params, true, callback);
    }

    public void messageSend(Integer bookingId, String text, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, String.valueOf(bookingId));
            params.put("message", text);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.SEND_BOOKING_MESSAGE, params, true, callback);
    }

    public void messagesSetRead(Integer bookingId) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, String.valueOf(bookingId));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.SET_MESSAGES_READ, params, true, new EmptySocketCallback());
    }

    public void resetPassword(String email, SocketCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put(EMAIL, email);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.RESET_PASSWORD, params, true, callback);
    }

    public void locationNearestGet(double lattitude, double longitude, SocketCallback callback) {
        JSONObject object = getParamsWithSession();
        try {
            object.put("lat", lattitude);
            object.put("long", longitude);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        run(Api.GET_NEAREST_LOCATION, object, true, callback);
    }


    public void carsBestGet(Integer locationId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put("location_id", locationId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.GET_BEST_CARS, params, true, callback);

    }

    public void carGalleryGet(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.GET_CAR_GALLERY, carIdRequest.convert(), callback);
    }

    public void userGet(SocketCallback callback) {
        run(Api.GET_USER_SETTINGS, getParamsWithSession(), true, callback);
    }

    public void passwordChange(String oldPass, String newPass, String passRepeat, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put("old_password", oldPass);
            params.put("new_password", newPass);
            params.put("new_password_again", passRepeat);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.SET_USER_PASSWORD, params, true, callback);


    }

    public void bookingCancelClient(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, bookingId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.CLIENT_CANCEL_BOOKING, params, true, callback);
    }

    public void bookingCancelPartner(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, bookingId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.PARTNER_CANCEL_BOOKING, params, true, callback);
    }


    public void notificationsGet(NotificationStatus status, SocketCallback callback) {

        JSONObject params = getParamsWithSession();
        try {
            params.put("filter", status.name().toLowerCase());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.GET_NOTIFICATION_LIST, params, true, callback);
    }

    public void chatsGet(SocketCallback callback) {

        JSONObject params = getParamsWithSession();

        run(Api.GET_CHAT_LIST, params, true, callback);
    }

    public void messagesGet(Integer bookingId, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(BOOKING_ID, bookingId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.GET_MY_BOOKING_CHAT, params, true, callback);

    }

    public void notificationSetStatus(String id, NotificationStatus status, SocketCallback callback) {

        JSONObject params = getParamsWithSession();
        try {
            params.put("notification_id", id);
            params.put("notification_status", status.name().toLowerCase());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        run(Api.SET_NOTIFICATION_STATUS, params, true, callback);

    }

    public void favoritesGet(SocketCallback callback) {
        run(Api.GET_FAVORITES, getParamsWithSession(), true, callback);
    }

    public void myCarsGet(SocketCallback callback) {
        run(Api.GET_MY_CARS, getParamsWithSession(), true, callback);
    }

    public void favoritesPut(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.ADD_FAVORITES, carIdRequest.convert(), callback);
    }

    public void favoritesDelete(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.REMOVE_FAVORITES, carIdRequest.convert(), callback);
    }

    public void userPicSet(FileRequest request, SocketCallback callback) {
        run(Api.SET_USER_PIC, request.convert(), callback);
    }

    public void subscribeOnNotifications(SocketCallback callback) {
        run(Api.NEW_NOTIFICATION, getParamsWithSession(), false, callback);
    }


    public void subscribeOnMessages(SocketCallback callback) {
        run(Api.NEW_BOOKING_MESSAGE, getParamsWithSession(), false, callback);
    }

    public void removeSubscription(Api api) {
        mSocket.off(api.getValue());
    }

    public void subscribeOnLogOut(SocketCallback callback) {
        run(Api.LOG_OUT, getParamsWithSession(), false, callback);
    }

    public void calendarGet(Integer carId, SocketCallback callback) {

        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.GET_CAR_CALENDAR, carIdRequest.convert(), callback);

    }

    public void calendarSet(Integer carId, CalendarForAPI data, SocketCallback callback) {

        CalendarRequest request = new CalendarRequest(carId, data);
        run(Api.SET_CAR_CALENDAR, request.convert(), callback);

    }

    public void bookingGetHistory(SocketCallback callback) {
        run(Api.GET_BOOKING_HISTORY, getParamsWithSession(), true, callback);
    }

    public void carSetPrice(Integer carId, Integer price, String currency, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(CAR_ID, carId);
            params.put("price", price);
            params.put("currency", currency);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.SET_CAR_PRICE, params, true, callback);

    }

    public void carSetServices(ServicesRequest request, SocketCallback callback) {
        run(Api.SET_CAR_SERVICES, request.convert(), callback);

    }

    public void carSetSpecs(SpecsRequest request, SocketCallback callback) {
        run(Api.SET_CAR_SPECS, request.convert(), callback);

    }


    public void carGetStatus(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.GET_CAR_STATUS, carIdRequest.convert(), callback);

    }

    public void carSetStatus(Integer carId, boolean status, SocketCallback callback) {
        JSONObject params = getParamsWithSession();
        try {
            params.put(CAR_ID, carId);
            JSONObject st = new JSONObject();
            st.put("published", status);
            params.put("car_status", st);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.SET_CAR_STATUS, params, true, callback);
    }

    public void popularDirectionsGet(SocketCallback callback) {

        run(Api.GET_POP_DIRECTS, getParamsWithSession(), true, callback);
    }

    public void tokenSet(String token) {

        if (token == null) {
            token = FirebaseInstanceId.getInstance().getToken();
        }

        JSONObject params = getParamsWithSession();
        try {
            params.put("device_token", token);
            params.put("type", "android");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        run(Api.SET_TOKEN, params, true, new EmptySocketCallback());
    }

    public void tokenDrop(String token) {

        if (token == null) {
            token = FirebaseInstanceId.getInstance().getToken();
        }

        JSONObject params = getParamsWithSession();
        try {
            params.put("device_token", token);
            params.put("type", "ios");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        run(Api.DROP_TOKEN, params, true, new ResponseAdapter(null) {
            @Override
            public void onSuccess(Api apiName, String json) {
                Log.d(TAG, "token successfully dropped");
            }
        });
    }


    public void reviewsGet(Integer carId, SocketCallback callback) {
        CarIdRequest carIdRequest = new CarIdRequest(carId);
        run(Api.GET_CAR_REVIEW_LIST, carIdRequest.convert(), callback);

    }

    public void reviewPut(ReviewRequest request, SocketCallback callback) {
        run(Api.ADD_CAR_REVIEW, request.convert(), callback);
    }

    public void parametersGet(SocketCallback callback, String currency) {
        JSONObject params = getParamsWithSession();
        try {
            params.put("currency", currency);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        run(Api.GET_PARAMETER_LIST, params, true, callback);
    }

    public void parametersGet(SocketCallback callback) {
        parametersGet(callback, Preferences.getCurrencyCode());
    }

    public void currencyListGet(SocketCallback callback) {
        run(Api.GET_CURRENCY_LIST, getParamsWithSession(), true, callback);
    }

    public void gallerySet(String request, SocketCallback callback) {
        run(Api.SET_GALLERY, request, callback);
    }

    public void locationGet(Integer carId, SocketCallback callback) {
        run(Api.GET_CAR_LOCATION, new CarIdRequest(carId).convert(), callback);
    }

    public void locationSet(Integer carId, Integer locationId, SocketCallback callback) {
        LocationSetRequest request = new LocationSetRequest(carId, locationId);
        run(Api.SET_CAR_LOCATION, request.convert(), callback);
    }

    public void carGetUrl(Integer carId, SocketCallback callback) {
        run(Api.GET_CAR_URL, new CarIdRequest(carId).convert(), callback);
    }

    public void carAdd(NewCar carData, SocketCallback callback) {
        NewCarRequest request = new NewCarRequest(carData);
        run(Api.ADD_CAR, request.convert(), callback);


    }


    private JSONObject getParamsWithSession() {
        JSONObject params = new JSONObject();

        try {
            params.put("session", Preferences.getSession());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return params;
    }


}
