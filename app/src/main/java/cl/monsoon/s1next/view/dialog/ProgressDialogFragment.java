package cl.monsoon.s1next.view.dialog;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;

import com.trello.rxlifecycle.FragmentEvent;
import com.trello.rxlifecycle.components.support.RxDialogFragment;

import cl.monsoon.s1next.App;
import cl.monsoon.s1next.data.User;
import cl.monsoon.s1next.data.api.S1Service;
import cl.monsoon.s1next.data.api.UserValidator;
import cl.monsoon.s1next.data.api.model.Account;
import cl.monsoon.s1next.data.api.model.wrapper.ResultWrapper;
import cl.monsoon.s1next.view.activity.BaseActivity;
import cl.monsoon.s1next.view.fragment.BaseFragment;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

/**
 * A dialog shows {@link ProgressDialog}.
 * Also wraps some related methods to request data.
 * <p>
 * This {@link DialogFragment} is retained in order
 * to retain request when configuration changes.
 *
 * @param <D> The data we want to request.
 */
abstract class ProgressDialogFragment<D> extends RxDialogFragment {

    S1Service mS1Service;

    private User mUser;
    UserValidator mUserValidator;

    @Override
    @CallSuper
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.AppComponent appComponent = App.getAppComponent(getContext());
        mS1Service = appComponent.getS1Service();
        mUser = appComponent.getUser();
        mUserValidator = appComponent.getUserValidator();

        // retain this Fragment
        setRetainInstance(true);

        request();
    }

    @NonNull
    @Override
    public final Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage(getProgressMessage());

        return progressDialog;
    }

    @Override
    @CallSuper
    public void onDestroyView() {
        // see https://code.google.com/p/android/issues/detail?id=17423
        Dialog dialog = getDialog();
        if (dialog != null) {
            getDialog().setOnDismissListener(null);
        }

        super.onDestroyView();
    }

    /**
     * @see BaseFragment#load()
     */
    private void request() {
        getSourceObservable().compose(bindUntilEvent(FragmentEvent.DESTROY))
                .observeOn(AndroidSchedulers.mainThread())
                .finallyDo(this::finallyDo)
                .subscribe(this::onNext, this::onError);
    }

    /**
     * @see BaseFragment#getSourceObservable()
     */
    abstract Observable<D> getSourceObservable();

    /**
     * A helpers method provides authenticity token.
     *
     * @param func A function that, when applied to the authenticity token, returns an
     *             Observable. And the {@link Observable} is what we want to return
     *             if we get authenticity token successful.
     * @return Returns {@link S1Service#refreshAuthenticityToken()}'s result if we
     * failed to get authenticity token, otherwise returns {@code func.call(authenticityToken)}.
     */
    final Observable<ResultWrapper> flatMapedWithAuthenticityToken(Func1<String, Observable<ResultWrapper>> func) {
        String authenticityToken = mUser.getAuthenticityToken();
        if (TextUtils.isEmpty(authenticityToken)) {
            return mS1Service.refreshAuthenticityToken().flatMap(resultWrapper -> {
                Account account = resultWrapper.getAccount();
                // return the ResultWrapper if we can not get the authenticity token
                // (if account has expired or network error)
                if (TextUtils.isEmpty(account.getAuthenticityToken())) {
                    return Observable.just(resultWrapper);
                } else {
                    mUserValidator.validate(account);
                    return func.call(account.getAuthenticityToken());
                }
            });
        } else {
            return func.call(authenticityToken);
        }
    }

    /**
     * @see BaseFragment#onNext(Object)
     */
    abstract void onNext(D data);

    /**
     * @see BaseFragment#onError(Throwable)
     */
    void onError(Throwable throwable) {
        ((BaseActivity) getActivity()).showText(throwable.toString());
    }

    /**
     * @see BaseFragment#finallyDo()
     */
    private void finallyDo() {
        dismissAllowingStateLoss();
    }

    protected abstract CharSequence getProgressMessage();
}
