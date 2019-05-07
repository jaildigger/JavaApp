package rent.auto;

import android.os.Bundle;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.yandex.metrica.YandexMetrica;

import java.util.HashMap;
import java.util.Map;

public class Analytics {

    public static final String LABEL = "label";
    private static Analytics instance;
    public BookingPlace mBookingPlace;
    public AddCarPlace mAddCarPlace;

    public static Analytics getInstance() {
        if (instance == null)
            instance = new Analytics();
        return instance;
    }

    public void sendEvent(String action, String label) {

        Map<String, Object> yaparams = new HashMap<>();
        yaparams.put(LABEL, label);
        YandexMetrica.reportEvent(action, yaparams);
        sendFirebaseEvent(action, label);
    }

    void sendFirebaseEvent(String action, String label) {
        Bundle gparams = new Bundle();
        gparams.putString(LABEL, label);
        App.get().getDefaultTracker().logEvent(action, gparams);
        App.get().getFacebookLogger().logEvent(action, gparams);

        CustomEvent customEvent = new CustomEvent(action);
        customEvent.putCustomAttribute(LABEL, label);

        Answers.getInstance().logCustom(customEvent);

    }

    public enum BookingPlace {

        notDefined("OTHER_PLACE_BOOKING"), bestCar("BEST_CAR_BOOKING"), popCar("POP_CAR_BOOKING"), searchCar("SEARCH_CAR_BOOKING");
        private final String value;
        BookingPlace(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }
    }

    public enum AddCarPlace {

        notDefined("OTHER_PLACE_ADD_CAR"), profileAdd("ADD_CAR"), homeAdd("HOME_ADD_CAR"),
        myCarsAdd("MY_CARS_ADD_CAR"),
        tutorialAdd("PLACE_CAR_TUTORIAL");
        private final String value;
        public String getValue() {
            return value;
        }
        AddCarPlace(String value) {
            this.value = value;
        }

    }
}
