/*
 * Copyright 2016 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import im.vector.VectorApp;
import im.vector.R;
import im.vector.activity.MXCActionBarActivity;
import im.vector.activity.VectorRoomDetailsActivity;
import im.vector.adapters.ParticipantAdapterItem;
import im.vector.adapters.VectorAddParticipantsAdapter;

import java.util.ArrayList;

public class VectorAddParticipantsFragment extends Fragment {
    private static final String LOG_TAG = "VectorAddParticipantsFragment";

    // class members
    private MXSession mSession;
    private Room mRoom;

    // fragment items
    private EditText mSearchEdit;
    private View mProgressView;
    private VectorAddParticipantsAdapter mAdapter;

    private boolean mIsEditionMode;
    private MenuItem mRemoveMembersItem;
    private MenuItem mSwitchDeletionItem;

    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        mAdapter.listOtherMembers();
                        mAdapter.refresh();
                    }
                }
            });
        }
    };

    // top view
    private View mViewHierarchy;

    public static VectorAddParticipantsFragment newInstance() {
        return new VectorAddParticipantsFragment();
    }

    @Override
    public void onPause() {
        super.onPause();

        // sanity check
        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // sanity check
        if (null != mRoom) {
            mRoom.addEventListener(mEventListener);
        }

        // sanity check
        if (null != mAdapter) {
            mAdapter.listOtherMembers();
            mAdapter.refresh();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewHierarchy = inflater.inflate(R.layout.fragment_vector_add_participants, container, false);

        Activity activity = getActivity();

        if (activity instanceof MXCActionBarActivity) {
            MXCActionBarActivity anActivity = (MXCActionBarActivity)activity;
            mRoom = anActivity.getRoom();
            mSession = anActivity.getSession();

            finalizeInit();
        }

        setHasOptionsMenu(true);

        return mViewHierarchy;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getActivity().getMenuInflater().inflate(R.menu.vector_room_details_add_people, menu);

        mRemoveMembersItem = menu.findItem(R.id.ic_action_room_details_delete);
        mSwitchDeletionItem = menu.findItem(R.id.ic_action_room_details_edition_mode);

        refreshMenuEntries();
    }

    /**
     * Refresh the menu entries according to the edition mode
     */
    private void refreshMenuEntries() {
        if (null != mRemoveMembersItem) {
            mRemoveMembersItem.setVisible(mIsEditionMode);
        }

        if (null != mSwitchDeletionItem) {
            mSwitchDeletionItem.setVisible(!mIsEditionMode);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_room_details_delete) {
            // TODO do something here
            mIsEditionMode = !mIsEditionMode;
            refreshMenuEntries();

        } else if (id ==  R.id.ic_action_room_details_edition_mode) {

            // TODO do something here
            mIsEditionMode = !mIsEditionMode;
            refreshMenuEntries();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Finalize the fragment initialization.
     */
    private void finalizeInit() {
        MXMediasCache mxMediasCache = mSession.getMediasCache();

        mProgressView = mViewHierarchy.findViewById(R.id.add_participants_progress_view);
        ListView participantsListView = (ListView)mViewHierarchy.findViewById(R.id.add_participants_members_list);
        mAdapter = new VectorAddParticipantsAdapter(getActivity(), R.layout.adapter_item_vector_add_participants, mSession, (null != mRoom) ? mRoom.getRoomId() : null, mxMediasCache);
        participantsListView.setAdapter(mAdapter);

        participantsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!TextUtils.isEmpty(mAdapter.getSearchedPattern())) {
                    ParticipantAdapterItem participant = mAdapter.getItem(position);

                    if (null != mRoom) {
                        final ArrayList<String> userIDs = new ArrayList<String>();
                        userIDs.add(participant.mUserId);

                        mProgressView.setVisibility(View.VISIBLE);

                        mRoom.invite(userIDs, new SimpleApiCallback<Void>(getActivity()) {
                            @Override
                            public void onSuccess(Void info) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onMatrixError(final MatrixError e) {
                                mProgressView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onUnexpectedError(final Exception e) {
                                mProgressView.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        mAdapter.addParticipant(participant);
                    }
                }

                // leave the search
                mSearchEdit.setText("");
            }
        });

        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
            }
        });

        mAdapter.setOnParticipantsListener(new VectorAddParticipantsAdapter.OnParticipantsListener() {
            @Override
            public void onRemoveClick(final ParticipantAdapterItem participantItem) {
                if (null == mRoom) {
                    mAdapter.removeParticipant(participantItem);
                } else {
                    String text = getActivity().getString(R.string.room_participants_remove_prompt_msg, participantItem.mDisplayName);

                    // The user is trying to leave with unsaved changes. Warn about that
                    new AlertDialog.Builder(VectorApp.getCurrentActivity())
                            .setTitle(R.string.room_participants_remove_prompt_title)
                            .setMessage(text)
                            .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();

                                    mProgressView.setVisibility(View.VISIBLE);

                                    mRoom.kick(participantItem.mUserId, new ApiCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void info) {
                                            mProgressView.setVisibility(View.GONE);
                                        }

                                        @Override
                                        public void onNetworkError(Exception e) {
                                            mProgressView.setVisibility(View.GONE);
                                            // display something
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                            mProgressView.setVisibility(View.GONE);
                                            // display something
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                            mProgressView.setVisibility(View.GONE);
                                            // display something
                                        }
                                    });
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create()
                            .show();
                }
            }

            @Override
            public void onLeaveClick() {
                // The user is trying to leave with unsaved changes. Warn about that
                new AlertDialog.Builder(VectorApp.getCurrentActivity())
                        .setTitle(R.string.room_participants_leave_prompt_title)
                        .setMessage(getActivity().getString(R.string.room_participants_leave_prompt_msg))
                        .setPositiveButton(R.string.leave, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                mProgressView.setVisibility(View.VISIBLE);

                                mRoom.leave(new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        mProgressView.setVisibility(View.GONE);
                                        // display something
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        mProgressView.setVisibility(View.GONE);
                                        // display something
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        mProgressView.setVisibility(View.GONE);
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        mProgressView.setVisibility(View.GONE);
                                    }
                                });
                                getActivity().finish();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();
            }
        });

        mSearchEdit = (EditText)mViewHierarchy.findViewById(R.id.add_participants_search_participants);
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                mAdapter.setSearchedPattern(s.toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        Button cancelButton = (Button)mViewHierarchy.findViewById(R.id.add_participants_cancel_search_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchEdit.setText("");
            }
        });

        mAdapter.setSearchedPattern(mSearchEdit.getText().toString());
        mAdapter.refresh();
    }

    /**
     * Dismiss any opened keyboard
     */
    public void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchEdit.getWindowToken(), 0);
    }

    /**
     * @return the participant User Ids except oneself.
     */
    public ArrayList<String> getUserIdsList() {
        return mAdapter.getUserIdsist();
    }
}
