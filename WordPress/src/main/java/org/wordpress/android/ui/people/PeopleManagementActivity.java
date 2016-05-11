package org.wordpress.android.ui.people;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.datasets.PeopleTable;
import org.wordpress.android.models.Blog;
import org.wordpress.android.models.Person;
import org.wordpress.android.ui.ActivityLauncher;
import org.wordpress.android.ui.accounts.BlogUtils;
import org.wordpress.android.ui.people.utils.PeopleUtils;
import org.wordpress.android.util.ToastUtils;

import java.util.List;

public class PeopleManagementActivity extends AppCompatActivity
        implements PeopleListFragment.OnPersonSelectedListener, PersonDetailFragment.OnChangeListener {
    private static final String KEY_PEOPLE_LIST_FRAGMENT = "people-list-fragment";
    private static final String KEY_PERSON_DETAIL_FRAGMENT = "person-detail-fragment";

    private Person mSelectedPerson;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.people_management_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.people);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        int localBlogId = BlogUtils.getBlogLocalId(WordPress.getCurrentBlog());
        Blog blog = WordPress.getBlog(localBlogId);

        if (savedInstanceState == null) {
            PeopleListFragment peopleListFragment = PeopleListFragment.newInstance(localBlogId);

            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, peopleListFragment, KEY_PEOPLE_LIST_FRAGMENT)
                    .commit();
        }

        if (blog != null) {
            refreshUsersList(blog.getDotComBlogId(), localBlogId);
        }
    }

    @Override
    public void finish() {
        super.finish();
        ActivityLauncher.slideOutToRight(this);
    }

    @Override
    public void onBackPressed() {
        if (!navigateBackToPeopleListFragment()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.remove_person) {
            confirmRemovePerson();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshUsersList(String dotComBlogId, final int localTableBlogId) {
        PeopleUtils.fetchUsers(dotComBlogId, localTableBlogId, new PeopleUtils.FetchUsersCallback() {
            @Override
            public void onSuccess(List<Person> peopleList) {
                PeopleTable.savePeople(peopleList, localTableBlogId);
                refreshOnScreenFragmentDetails();
            }

            @Override
            public void onError() {
                ToastUtils.showToast(PeopleManagementActivity.this,
                        R.string.error_fetch_people_list,
                        ToastUtils.Duration.LONG);
            }
        });
    }

    @Override
    public void onPersonSelected(Person person) {
        FragmentManager fragmentManager = getFragmentManager();
        PersonDetailFragment personDetailFragment = (PersonDetailFragment) fragmentManager
                .findFragmentByTag(KEY_PERSON_DETAIL_FRAGMENT);

        mSelectedPerson = person;

        long personID = person.getPersonID();
        int localTableBlogID = person.getLocalTableBlogId();
        if (personDetailFragment == null) {
            personDetailFragment = PersonDetailFragment.newInstance(personID, localTableBlogID);
        } else {
            personDetailFragment.setPersonDetails(personID, localTableBlogID);
        }
        if (!personDetailFragment.isAdded()) {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.add(R.id.fragment_container, personDetailFragment, KEY_PERSON_DETAIL_FRAGMENT);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    @Override
    public void onRoleChanged(long personID, int localTableBlogId, String newRole) {
        Person person = PeopleTable.getPerson(personID, localTableBlogId);
        if (person == null || newRole == null || newRole.equalsIgnoreCase(person.getRole())) {
            return;
        }
        PeopleUtils.updateRole(person.getBlogId(), person.getPersonID(), newRole, localTableBlogId,
                new PeopleUtils.UpdateUserCallback() {
            @Override
            public void onSuccess(Person person) {
                PeopleTable.save(person);
                refreshOnScreenFragmentDetails();
            }

            @Override
            public void onError() {
                ToastUtils.showToast(PeopleManagementActivity.this,
                        R.string.error_update_role,
                        ToastUtils.Duration.LONG);
            }
        });
    }

    private void confirmRemovePerson() {
        if (mSelectedPerson == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Calypso_AlertDialog);
        builder.setTitle(getString(R.string.person_remove_confirmation_title, mSelectedPerson.getDisplayName()));
        builder.setMessage(getString(R.string.person_remove_confirmation_message, mSelectedPerson.getDisplayName()));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                removeSelectedPerson();
            }
        });
        builder.show();
    }

    private void removeSelectedPerson() {
        if (mSelectedPerson == null) {
            return;
        }
        PeopleUtils.removePerson(mSelectedPerson.getBlogId(),
                mSelectedPerson.getPersonID(),
                mSelectedPerson.getLocalTableBlogId(),
                new PeopleUtils.RemoveUserCallback() {
                    @Override
                    public void onSuccess(long personID, int localTableBlogId) {
                        // remove the person from db, navigate back to list fragment and refresh it
                        Person person = PeopleTable.getPerson(personID, localTableBlogId);
                        String text;
                        if (person != null) {
                            PeopleTable.deletePerson(personID, localTableBlogId);
                            text = getString(R.string.person_removed, person.getUsername());
                        } else {
                            text = getString(R.string.person_removed_general);
                        }

                        ToastUtils.showToast(PeopleManagementActivity.this, text, ToastUtils.Duration.LONG);

                        navigateBackToPeopleListFragment();
                        refreshPeopleListFragment();
                    }

                    @Override
                    public void onError() {
                        ToastUtils.showToast(PeopleManagementActivity.this,
                                R.string.error_remove_user,
                                ToastUtils.Duration.LONG);
                    }
                });
    }

    // This helper method is used after a successful network request
    private void refreshOnScreenFragmentDetails() {
        FragmentManager fragmentManager = getFragmentManager();
        PersonDetailFragment personDetailFragment = (PersonDetailFragment) fragmentManager
                .findFragmentByTag(KEY_PERSON_DETAIL_FRAGMENT);

        if (personDetailFragment != null) {
            personDetailFragment.refreshPersonDetails();
        }

        refreshPeopleListFragment();
    }

    private void refreshPeopleListFragment() {
        FragmentManager fragmentManager = getFragmentManager();
        PeopleListFragment peopleListFragment = (PeopleListFragment) fragmentManager
                .findFragmentByTag(KEY_PEOPLE_LIST_FRAGMENT);
        if (peopleListFragment != null) {
            peopleListFragment.refreshPeopleList();
        }
    }

    private boolean navigateBackToPeopleListFragment() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();

            // navigated back to people list
            mSelectedPerson = null;
            return true;
        }
        return false;
    }
}
