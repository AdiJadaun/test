/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.bluetooth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

/**
 * A dialogFragment used by {@link BluetoothPairingDialog} to create an appropriately styled dialog
 * for the bluetooth device.
 */
public class BluetoothPairingDialogFragment extends InstrumentedDialogFragment implements
        TextWatcher, OnClickListener {

    private static final String TAG = "BTPairingDialogFragment";

    private AlertDialog.Builder mBuilder;
    private BluetoothPairingController mPairingController;
    private AlertDialog mDialog;
    private EditText mPairingView;

    /**
     * The interface we expect a listener to implement. Typically this should be done by
     * the controller.
     */
    public interface BluetoothPairingDialogListener {

        void onDialogNegativeClick(BluetoothPairingDialogFragment dialog);

        void onDialogPositiveClick(BluetoothPairingDialogFragment dialog);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (mPairingController == null) {
            throw new IllegalStateException(
                    "Must call setPairingController() before showing dialog");
        }
        mBuilder = new AlertDialog.Builder(getActivity());
        mDialog = setupDialog();
        mDialog.setCanceledOnTouchOutside(false);
        return mDialog;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        // enable the positive button when we detect potentially valid input
        Button positiveButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setEnabled(mPairingController.isPasskeyValid(s));
        }
        // notify the controller about user input
        mPairingController.updateUserInput(s.toString());
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mPairingController.onDialogPositiveClick(this);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            mPairingController.onDialogNegativeClick(this);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLUETOOTH_DIALOG_FRAGMENT;
    }

    /**
     * Sets the controller that the fragment should use. this method MUST be called
     * before you try to show the dialog or an error will be thrown. An implementation
     * of a pairing controller can be found at {@link BluetoothPairingController}.
     */
    public void setPairingController(BluetoothPairingController pairingController) {
        mPairingController = pairingController;
    }

    /**
     * Creates the appropriate type of dialog and returns it.
     */
    private AlertDialog setupDialog() {
        AlertDialog dialog;
        switch (mPairingController.getDialogType()) {
            case BluetoothPairingController.USER_ENTRY_DIALOG:
                dialog = createUserEntryDialog();
                break;
            case BluetoothPairingController.CONFIRMATION_DIALOG:
                dialog = createConsentDialog();
                break;
            case BluetoothPairingController.DISPLAY_PASSKEY_DIALOG:
                dialog = createDisplayPasskeyOrPinDialog();
                break;
            default:
                dialog = null;
                Log.e(TAG, "Incorrect pairing type received, not showing any dialog");
        }
        return dialog;
    }

    /**
     * Returns a dialog with UI elements that allow a user to provide input.
     */
    private AlertDialog createUserEntryDialog() {
        mBuilder.setTitle(getString(R.string.bluetooth_pairing_request,
                mPairingController.getDeviceName()));
        mBuilder.setView(createPinEntryView());
        mBuilder.setPositiveButton(getString(android.R.string.ok), this);
        mBuilder.setNegativeButton(getString(android.R.string.cancel), this);
        AlertDialog dialog = mBuilder.create();
        dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
        return dialog;
    }

    /**
     * Creates the custom view with UI elements for user input.
     */
    private View createPinEntryView() {
        View view = getActivity().getLayoutInflater().inflate(R.layout.bluetooth_pin_entry, null);
        TextView messageViewCaptionHint = (TextView) view.findViewById(R.id.pin_values_hint);
        TextView messageView2 = (TextView) view.findViewById(R.id.message_below_pin);
        CheckBox alphanumericPin = (CheckBox) view.findViewById(R.id.alphanumeric_pin);
        CheckBox contactSharing = (CheckBox) view.findViewById(
                R.id.phonebook_sharing_message_entry_pin);
        contactSharing.setText(getString(R.string.bluetooth_pairing_shares_phonebook,
                mPairingController.getDeviceName()));
        EditText pairingView = (EditText) view.findViewById(R.id.text);

        contactSharing.setVisibility(mPairingController.isProfileReady()
                ? View.GONE : View.VISIBLE);
        contactSharing.setOnCheckedChangeListener(mPairingController);
        contactSharing.setChecked(mPairingController.getContactSharingState());

        mPairingView = pairingView;

        pairingView.addTextChangedListener(this);
        alphanumericPin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // change input type for soft keyboard to numeric or alphanumeric
            if (isChecked) {
                mPairingView.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                mPairingView.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        int messageId = mPairingController.getDeviceVariantMessageID();
        int messageIdHint = mPairingController.getDeviceVariantMessageHint();
        int maxLength = mPairingController.getDeviceMaxPasskeyLength();
        alphanumericPin.setVisibility(mPairingController.pairingCodeIsAlphanumeric()
                ? View.VISIBLE : View.GONE);

        messageViewCaptionHint.setText(messageIdHint);
        messageView2.setText(messageId);
        pairingView.setInputType(InputType.TYPE_CLASS_NUMBER);
        pairingView.setFilters(new InputFilter[]{
                new LengthFilter(maxLength)});

        return view;
    }

    /**
     * Creates a dialog with UI elements that allow the user to confirm a pairing request.
     */
    private AlertDialog createConfirmationDialog() {
        mBuilder.setTitle(getString(R.string.bluetooth_pairing_request,
                mPairingController.getDeviceName()));
        mBuilder.setView(createView());
        mBuilder.setPositiveButton(getString(R.string.bluetooth_pairing_accept),
                this);
        mBuilder.setNegativeButton(getString(R.string.bluetooth_pairing_decline),
                this);
        AlertDialog dialog = mBuilder.create();
        return dialog;
    }

    /**
     * Creates a dialog with UI elements that allow the user to consent to a pairing request.
     */
    private AlertDialog createConsentDialog() {
        return createConfirmationDialog();
    }

    /**
     * Creates a dialog that informs users of a pairing request and shows them the passkey/pin
     * of the device.
     */
    private AlertDialog createDisplayPasskeyOrPinDialog() {
        mBuilder.setTitle(getString(R.string.bluetooth_pairing_request,
                mPairingController.getDeviceName()));
        mBuilder.setView(createView());
        mBuilder.setNegativeButton(getString(android.R.string.cancel), this);
        AlertDialog dialog = mBuilder.create();

        // Tell the controller the dialog has been created.
        mPairingController.notifyDialogDisplayed();

        return dialog;
    }

    /**
     * Creates a custom view for dialogs which need to show users additional information but do
     * not require user input.
     */
    private View createView() {
        View view = getActivity().getLayoutInflater().inflate(R.layout.bluetooth_pin_confirm, null);
        TextView pairingViewCaption = (TextView) view.findViewById(R.id.pairing_caption);
        TextView pairingViewContent = (TextView) view.findViewById(R.id.pairing_subhead);
        TextView messagePairing = (TextView) view.findViewById(R.id.pairing_code_message);
        CheckBox contactSharing = (CheckBox) view.findViewById(
                R.id.phonebook_sharing_message_confirm_pin);
        contactSharing.setText(getString(R.string.bluetooth_pairing_shares_phonebook,
                mPairingController.getDeviceName()));

        contactSharing.setVisibility(
                mPairingController.isProfileReady() ? View.GONE : View.VISIBLE);
        contactSharing.setChecked(mPairingController.getContactSharingState());
        contactSharing.setOnCheckedChangeListener(mPairingController);

        messagePairing.setVisibility(mPairingController.isDisplayPairingKeyVariant()
                ? View.VISIBLE : View.GONE);
        if (mPairingController.hasPairingContent()) {
            pairingViewCaption.setVisibility(View.VISIBLE);
            pairingViewContent.setVisibility(View.VISIBLE);
            pairingViewContent.setText(mPairingController.getPairingContent());
        }
        return view;
    }

}