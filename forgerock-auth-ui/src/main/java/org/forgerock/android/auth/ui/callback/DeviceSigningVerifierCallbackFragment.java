/*
 * Copyright (c) 2022 ForgeRock. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package org.forgerock.android.auth.ui.callback;


import static android.view.View.GONE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.forgerock.android.auth.FRListener;
import org.forgerock.android.auth.callback.DeviceSigningVerifierCallback;
import org.forgerock.android.auth.ui.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class DeviceSigningVerifierCallbackFragment extends CallbackFragment<DeviceSigningVerifierCallback> {

    private TextView message;
    private ProgressBar progressBar;

    public DeviceSigningVerifierCallbackFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_device_signing_verifier_callback, container, false);
        message = view.findViewById(R.id.message);
        progressBar = view.findViewById(R.id.signingProgress);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        proceed();
    }

    private void proceed() {
        callback.sign(getContext(), new FRListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                getActivity().runOnUiThread(() -> {
                    message.setVisibility(GONE);
                    progressBar.setVisibility(GONE);
                    next();
                });
            }

            @Override
            public void onException(Exception e) {
                getActivity().runOnUiThread(() -> {
                    message.setVisibility(GONE);
                    progressBar.setVisibility(GONE);
                    next();
                });
            }
        });
    }
}
