package pro.glideim.sdk.api;

import io.reactivex.annotations.NonNull;

import org.jetbrains.annotations.NotNull;

import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

public abstract class TestResObserver<T> implements Observer<T>, SingleObserver<T> {
    @Override
    public void onSuccess(@NonNull T t) {
        onNext(t);
    }

    @Override
    public void onSubscribe(@NotNull Disposable d) {
        System.out.println("TestResObserver.onSubscribe");
    }

    @Override
    public void onError(@NotNull Throwable e) {
        System.out.println("TestResObserver.onError");
        e.printStackTrace();
    }


    @Override
    public void onComplete() {
        System.out.println("TestResObserver.onComplete");
    }
}
