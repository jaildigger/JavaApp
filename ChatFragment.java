package rent.auto.chats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.constraint.Guideline;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.joda.time.DateTime;

import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rent.auto.Analytics;
import rent.auto.App;
import rent.auto.BackPressFragment;
import rent.auto.R;
import rent.auto.bookings.BookingsFragment;
import rent.auto.model.BookingData;
import rent.auto.model.ChatMessages;
import rent.auto.model.Message;
import rent.auto.model.NewMessage;
import rent.auto.model.constant.BookingStatus;
import rent.auto.model.lab.BookingLab;
import rent.auto.model.view.KeyboardDismissingRecyclerView;
import rent.auto.socket.Api;
import rent.auto.socket.ResponseAdapter;
import rent.auto.socket.SocketManager;
import rent.auto.util.Broadcast;
import rent.auto.util.GlideApp;
import rent.auto.util.Helpers;

import static rent.auto.bookings.BookingsFragment.ACTION_NEW_BOOKING;
import static rent.auto.bookings.BookingsFragment.EXTRA_BOOKING_ID;


public class ChatFragment extends BackPressFragment {

    private final static String ARG_BOOKING_ID = "booking_id";
    private static final String EXTRA_JSON = "rent.auto.extra.json";
    private final static String ARG_TITLE = "title";
    private final static String ARG_CAR_TITLE = "car_title";
    private final static String ARG_LOCKED = "locked";
    public static final String OPEN = "open";
    public static final String CLOSED = "closed";
    public static final int DELAY_MILLIS = 1000;
    private final BroadcastReceiver incomeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {

            String json = intent.getStringExtra(EXTRA_JSON);
            NewMessage newMessage = NewMessage.get(json);

            if (!bookingId.equals(newMessage.getBookingId()))
                return;
            addMessage(newMessage.getMessage());

        }
    };
    @BindView(R.id.list)
    KeyboardDismissingRecyclerView rv;
    private ChatMessages chatMessages;
    @BindView(R.id.car_title)
    TextView carTitleView;
    private BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer receivedId = intent.getIntExtra(EXTRA_BOOKING_ID, 0);
            if (booking == null || !bookingId.equals(receivedId))
                return;
            runIfAlive(() -> {
                booking.setStatus(BookingStatus.CANCELED);
                if (buttonAttach.getTag().equals(OPEN))
                    buttonAttach.performClick();
                updateBookingUi(booking);
            });
        }
    };
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    //BOTTOM
    @BindView(R.id.attach)
    ImageButton buttonAttach;
    @BindView(R.id.message_button)
    Button buttonMessage;
    @BindView(R.id.button_send)
    ImageButton buttonSend;
    @BindView(R.id.text_message)
    AppCompatEditText textMessage;
    @BindView(R.id.chat_layout)
    LinearLayout chatLayout;
    @BindView(R.id.docs_button)
    Button buttonDocs;
    @BindView(R.id.cancel_booking)
    Button buttonCancelBook;
    @BindView(R.id.toolbar_layout)
    AppBarLayout appBarLayout;
    //TOP
    @BindView(R.id.guide_line_parent_start)
    Guideline guideline;
    @BindView(R.id.order_photo)
    ImageView orderPhoto;
    private Callbacks callbacks;
    @BindView(R.id.period)
    TextView textPeriod;

    private boolean isBookedByMe = false;

    private final BroadcastReceiver keyboardHiddenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            if (getContext() == null)
                return;

            runIfAlive(() -> {
                buttonMessage.setVisibility(View.VISIBLE);
                textMessage.setVisibility(View.INVISIBLE);
                buttonSend.setVisibility(View.INVISIBLE);
            });
        }


    };
    private ChatAdapter adapter;
    private Integer bookingId = 0;
    private String title = "";
    private String carTitle = "";
    private boolean isDocsAlreadySent = false;

    @BindView(R.id.status)
    TextView textStatus;
    @BindView(R.id.button_details)
    Button buttonDetails;
    @BindView(R.id.details_layout)
    ConstraintLayout detailsLayout;
    ConstraintLayout.LayoutParams guideParams;
    private boolean isLocked;

    private void addItem(Message message) {
        adapter.addItem(message);
        LinearLayoutManager layoutManager = (LinearLayoutManager) rv.getLayoutManager();

        if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
            runIfAlive(() -> appBarLayout.setExpanded(true, true));
        } else {
            appBarLayout.setExpanded(false, true);
        }
    }


    private void addMessage(Message message) {
        int index = -1;
        if (adapter != null) {

            if (!message.isDeliveredByServer() || !message.isMy() || message.isMy() && message.getAuthorId() == 0) {
                addItem(message);
            } else {
                index = adapter.setItem(message);
                if (index == -1) {
                    addItem(message);
                }
            }
        }

        int finalIndex = index;
        runIfAlive(() -> {

            if (adapter == null) {
                rv.setAdapter(adapter);
            } else {
                if (rv.getAdapter() == null) {
                    rv.setAdapter(adapter);
                }
                if (finalIndex > -1) {
                    adapter.notifyItemChanged(finalIndex);
                } else {
                    adapter.notifyItemInserted(adapter.getItemCount() - 1);
                    rv.post(() -> rv.smoothScrollToPosition(adapter.getItemCount() - 1));
                    new Handler().postDelayed(() -> SocketManager.get().messagesSetRead(bookingId), DELAY_MILLIS);

                }
            }

        });

    }


    public static ChatFragment newInstance(ChatParams chatParams) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_BOOKING_ID, chatParams.getBookingId());
        args.putString(ARG_TITLE, chatParams.getTitle());
        args.putString(ARG_CAR_TITLE, chatParams.getCarTitle());
        args.putBoolean(ARG_LOCKED, chatParams.isLocked());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        callbacks = (Callbacks) context;
        IntentFilter filter = new IntentFilter(ChatsChildFragment.ACTION_NEW_MESSAGE);
        LocalBroadcastManager.getInstance(context).registerReceiver(incomeReceiver, filter);
        IntentFilter filter2 = new IntentFilter(Broadcast.ACTION_KEYBOARD_HIDDEN);
        LocalBroadcastManager.getInstance(context).registerReceiver(keyboardHiddenReceiver, filter2);
        IntentFilter filter3 = new IntentFilter(ACTION_NEW_BOOKING);
        LocalBroadcastManager.getInstance(context).registerReceiver(cancelReceiver, filter3);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
        if (adapter != null)
            adapter.callbacks = null;
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).unregisterReceiver(incomeReceiver);
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).unregisterReceiver(keyboardHiddenReceiver);
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).unregisterReceiver(cancelReceiver);
    }

    private BookingData booking;

    @OnClick(R.id.button_send)
    public void onMessageSend() {
        if (TextUtils.isEmpty(textMessage.getText()))
            return;
        String text = Objects.requireNonNull(textMessage.getText()).toString();
        SocketManager.get().messageSend(bookingId, text, new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {

                runIfAlive(() -> {
                    Message message = new Message();
                    message.setAuthorId(-1);
                    message.setText(text);
                    message.setMy(true);
                    message.setDeliveredByServer(false);
                    message.setDatetime(DateTime.now().toString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
                    addMessage(message);
                    textMessage.setText("");
                });
            }
        });
    }

    @OnClick(R.id.message_button)
    public void onMessageClick() {
        buttonMessage.setVisibility(View.INVISIBLE);
        textMessage.setVisibility(View.VISIBLE);
        buttonSend.setVisibility(View.VISIBLE);
        textMessage.requestFocus();
        InputMethodManager imm = (InputMethodManager) Objects.requireNonNull(getContext()).getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bookingId = getArguments().getInt(ARG_BOOKING_ID);
            title = getArguments().getString(ARG_TITLE);
            carTitle = getArguments().getString(ARG_CAR_TITLE);
            isLocked = getArguments().getBoolean(ARG_LOCKED);
        }


    }

    private boolean isFirstWasVisible = false;

    @OnClick(R.id.button_details)
    public void onButtonDetails() {
        if (chatMessages == null || chatMessages.getChatData() == null)
            return;
        callbacks.onClickButtonDetails(bookingId, chatMessages.getChatData().getUserRole());
    }


    private void getBookingDetails() {
        ChatMessages.BookingRole role = chatMessages.getChatData().getUserRole();
        if (role == null)
            return;

        if (ChatMessages.BookingRole.CLIENT == role) {
            isBookedByMe = true;
            booking = BookingLab.get(getContext()).getBooking(bookingId);
            if (booking == null) {
                SocketManager.get().bookingGet(bookingId, new ResponseAdapter(getActivity()) {
                    @Override
                    public void onSuccess(Api apiName, String json) {
                        booking = BookingData.get(json);
                        runIfAlive(() -> updateBookingUi(booking));
                    }
                });
            } else {
                runIfAlive(() -> updateBookingUi(booking));
            }


        } else {
            SocketManager.get().bookingOwnerGet(bookingId, new ResponseAdapter(getActivity()) {
                @Override
                public void onSuccess(Api apiName, String json) {
                    booking = BookingData.get(json);
                    runIfAlive(() -> updateBookingUi(booking));
                }
            });
        }
    }

    private void updateBookingUi(final BookingData booking) {
        GlideApp.with(Objects.requireNonNull(getActivity())).load(booking.getCar().getPhotoUrl()).into(orderPhoto);
        textStatus.setText(Helpers.visibleStatus(booking.getStatus(), booking.getCheckOut()).getValue());
        textPeriod.setText(Helpers.formatPeriod(Objects.requireNonNull(getContext()), booking.getCheckIn(), booking.getCheckOut()));
        buttonDetails.setEnabled(true);
        checkDocs(booking);

    }

    private void checkDocs(BookingData booking) {

        if (BookingStatus.CANCELED == booking.getStatus()) {
            return;
        }

        if (isBookedByMe) {
            SocketManager.get().docsBookingGet(bookingId, new ResponseAdapter(getActivity()) {
                @Override
                public void onSuccess(Api apiName, String json) {
                    JsonObject j = App.get().getGson().fromJson(json, JsonObject.class);
                    JsonArray docs = App.get().getGson().fromJson(j.get("document_list"), JsonArray.class);
                    runIfAlive(() -> {
                        if (docs != null && docs.size() > 0) {
                            isDocsAlreadySent = true;
                            if (adapter != null) {
                                adapter.setDocsSent();
                            }

                        } else {
                            buttonDocs.setText(R.string.upload_docs);
                            isDocsAlreadySent = false;
                            if (adapter != null) {
                                adapter.setDocsNotSent();
                            }
                        }
                        buttonDocs.setOnClickListener(v -> callbacks.onClickUploadDocs(booking));
                    });
                }


            });
        } else {

            SocketManager.get().docsBookingGet(bookingId, new ResponseAdapter(getActivity()) {
                @Override
                public void onSuccess(Api apiName, String json) {
                    JsonObject j = App.get().getGson().fromJson(json, JsonObject.class);
                    JsonArray docs = App.get().getGson().fromJson(j.get("document_list"), JsonArray.class);
                    runIfAlive(() -> {
                        if (docs != null && docs.size() > 0 ||
                                BookingStatus.COMPLETE == Helpers.visibleStatus(booking.getStatus(), booking.getCheckOut()) ||
                                booking.getDocumentStatus1() != null && booking.getDocumentStatus1().equals("required") ||
                                booking.getDocumentStatus2() != null && booking.getDocumentStatus2().equals("required")) {
                            isDocsAlreadySent = true;
                            if (adapter != null) {
                                adapter.setDocsSent();
                            }

                        } else {
                            buttonDocs.setText(R.string.request_docs);
                            isDocsAlreadySent = false;
                            if (adapter != null) {
                                adapter.setDocsNotSent();
                            }
                            buttonDocs.setOnClickListener(v -> SocketManager.get().docRequest(bookingId, new ResponseAdapter(getActivity()) {
                                @Override
                                public void onSuccess(Api apiName, String json) {
                                    isDocsAlreadySent = true;
                                    runIfAlive(() -> {
                                        if (adapter != null) {
                                            adapter.setDocsSent();
                                        }
                                        buttonAttach.performClick();
                                    });
                                }
                            }));

                        }
                    });
                }
            });
        }
        buttonDocs.setEnabled(true);
    }

    private void cancelMyBooking() {
        if (isAlive())
            progressBar.setVisibility(View.VISIBLE);
        if (booking == null || booking.getId() == null)
            return;
        SocketManager.get().bookingCancelClient(booking.getId(), new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {
                BookingLab.get(getContext()).remove(booking.getId());
                booking.setStatus(BookingStatus.CANCELED);
                runIfAlive(() -> {
                    buttonAttach.performClick();
                    updateBookingUi(booking);
                    LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).
                            sendBroadcast(new Intent(BookingsFragment.ACTION_REMOVE_BOOKING));
                    manageToolbarScrolling();
                });
                Analytics.getInstance().sendEvent("CANCEL_BOOKING", "");

            }

            @Override
            public void onResponse(Api api) {
                runIfAlive(() -> {
                    progressBar.setVisibility(View.INVISIBLE);
                });
            }
        });
    }

    private void cancelClientBooking() {
        if (isAlive())
            progressBar.setVisibility(View.VISIBLE);
        SocketManager.get().bookingCancelPartner(booking.getId(), new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {
                runIfAlive(() -> {
                    booking.setStatus(BookingStatus.CANCELED);
                    buttonAttach.performClick();
                    updateBookingUi(booking);
                    manageToolbarScrolling();
                });
            }

            @Override
            public void onResponse(Api api) {
                runIfAlive(() -> {
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private boolean isDetailsCollapsed = false;
    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            Log.d(App.TAG, String.valueOf(dy));
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 && !isFirstWasVisible && dy < 0) {
                expand();
                return;
            }
            if (dy > 0 && !isDetailsCollapsed) {
                collapse();
            }
        }
    };

    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);

    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
    }

    @OnClick(R.id.attach)
    public void onButtonAttach() {


        TypedValue tv = new TypedValue();
        int actionBarHeight = 0;
        if (Objects.requireNonNull(getActivity()).getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }
        if (buttonAttach.getTag().equals(OPEN)) {
            buttonAttach.setEnabled(false);
            buttonCancelBook.setVisibility(View.GONE);
            buttonDocs.setVisibility(View.GONE);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(Objects.requireNonNull(getContext()), R.drawable.ic_add));
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) rv.getLayoutParams();
            params.setMargins(0, dpToPx(8), 0, actionBarHeight);
            rv.requestLayout();
            buttonAttach.setTag(CLOSED);
            buttonAttach.setEnabled(true);
        } else {
            if (booking == null)
                return;
            buttonAttach.setEnabled(false);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(Objects.requireNonNull(getContext()), R.drawable.ic_add_black));
            DateTime checkIn = new DateTime(booking.getCheckIn());
            if (isBookedByMe) {
                if (checkIn.minusDays(2).isAfterNow() && BookingStatus.CANCELED != booking.getStatus()) {
                    buttonCancelBook.setVisibility(View.VISIBLE);
                    buttonCancelBook.setOnClickListener(v -> cancelMyBooking());
                } else {
                    buttonCancelBook.setVisibility(View.GONE);
                }
            } else {
                if (checkIn.isAfterNow() && BookingStatus.CANCELED != booking.getStatus()) {
                    buttonCancelBook.setVisibility(View.VISIBLE);
                    buttonCancelBook.setOnClickListener(v -> cancelClientBooking());
                } else {
                    buttonCancelBook.setVisibility(View.GONE);
                }
            }

            boolean cancelIsVisible = buttonCancelBook.getVisibility() == View.VISIBLE;
            int coef = 3;
            if (!cancelIsVisible && isDocsAlreadySent) {
                coef = 1;
            } else if (!cancelIsVisible || isDocsAlreadySent) {
                coef = 2;
            }
            if (!isDocsAlreadySent)
                buttonDocs.setVisibility(View.VISIBLE);

            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) rv.getLayoutParams();
            params.setMargins(0, dpToPx(8), 0, actionBarHeight * coef);
            rv.requestLayout();
            buttonAttach.setTag(OPEN);
            buttonAttach.setEnabled(true);
        }

    }

    private void manageToolbarScrolling() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) rv.getLayoutManager();
        new Handler().postDelayed(() -> {
            if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
                expand();
            } else {
                runIfAlive(() -> {
                    collapse();
                    rv.postDelayed(() -> rv.smoothScrollToPosition(adapter.getItemCount() - 1), 100);
                });

            }
        }, 100);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View result = inflater.inflate(R.layout.fragment_chat, container, false);
        ButterKnife.bind(this, result);
        initToolbar(toolbar, title);
        initHomeButton();
        guideParams = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();

        ((LinearLayoutManager) Objects.requireNonNull(rv.getLayoutManager())).setSmoothScrollbarEnabled(true);
        getMessages();
        carTitleView.setText(carTitle);
        buttonAttach.setTag(CLOSED);
        if (isLocked) {
            buttonAttach.setEnabled(false);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(Objects.requireNonNull(getContext()), R.drawable.ic_add_gray));
        }
        rv.addOnScrollListener(scrollListener);
        return result;
    }

    private void expand() {
        isFirstWasVisible = true;
        isDetailsCollapsed = false;
        updateConstraints(R.layout.chat_booking_details_expanded);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) rv.getLayoutParams();
        params.topMargin = dpToPx(24);
        rv.setLayoutParams(params);

    }

    private void collapse() {
        isDetailsCollapsed = true;
        isFirstWasVisible = false;
        if (getContext() == null)
            return;
        updateConstraints(R.layout.chat_booking_details_collapsed);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) rv.getLayoutParams();
        params.topMargin = dpToPx(-16);
        rv.setLayoutParams(params);
    }

    private void updateConstraints(@LayoutRes int layout) {
        if (getContext() == null)
            return;
        ConstraintSet newConstraints = new ConstraintSet();
        if (getContext() == null)
            return;
        newConstraints.clone(getContext(), layout);
        if (getContext() == null)
            return;
        newConstraints.applyTo(detailsLayout);
        if (getContext() == null)
            return;
        TransitionManager.beginDelayedTransition(detailsLayout);
    }


    private void getMessages() {
        showProgress();
        SocketManager.get().messagesGet(bookingId, new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {
                chatMessages = ChatMessages.get(json);
                getBookingDetails();
                List<Message> messages = chatMessages.getMessages();
                boolean isEmpty = messages == null || messages.isEmpty();
                runIfAlive(() -> {
                    if (isEmpty)
                        return;

                    if (adapter == null) {
                        adapter = new ChatAdapter(AdapterParams.Builder(getActivity())
                                .items(messages)
                                .bookingId(bookingId)
                                .carTitle(carTitle)
                                .isCarOwner(!isBookedByMe)
                                .isLocked(isLocked)
                                .role(chatMessages.getChatData().getUserRole())
                                .build());
                    } else {
                        adapter.setList(messages);
                    }
                    rv.setAdapter(adapter);
                    LinearLayoutManager layoutManager = (LinearLayoutManager) rv.getLayoutManager();
                    new Handler().postDelayed(() -> {
                        if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() != 0) {
                            collapse();
                            int index = adapter.getItemCount() - 1;
                            for (Message message : messages) {
                                if (!message.isRead()) {
                                    index = messages.indexOf(message);
                                    break;
                                }
                            }

                            int finalIndex = index;
                            rv.postDelayed(() -> rv.smoothScrollToPosition(finalIndex), 100);
                        }
                        new Handler().postDelayed(() -> SocketManager.get().messagesSetRead(bookingId), DELAY_MILLIS);
                    }, 300);

                });


            }

            @Override
            public void onResponse(Api api) {
                runIfAlive(() -> hideProgress());
            }
        });
    }

    public interface Callbacks {
        void onClickButtonDetails(Integer bookingId, ChatMessages.BookingRole bookingRole);

        void onClickUploadDocs(BookingData bookingData);

    }


    @Override
    protected boolean onOtherMenuItemSelected(MenuItem item) {
        return false;
    }

    @Override
    protected void doOtherBackPressStuff() {
        runIfAlive(() -> {
            hideKeys();
            Objects.requireNonNull(getActivity()).onBackPressed();
        });
    }
}
