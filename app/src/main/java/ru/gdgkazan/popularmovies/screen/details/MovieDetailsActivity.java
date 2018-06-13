package ru.gdgkazan.popularmovies.screen.details;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Slide;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmResults;
import ru.gdgkazan.popularmovies.R;
import ru.gdgkazan.popularmovies.model.content.Movie;
import ru.gdgkazan.popularmovies.model.content.Review;
import ru.gdgkazan.popularmovies.model.content.ReviewAndVideo;
import ru.gdgkazan.popularmovies.model.content.Video;
import ru.gdgkazan.popularmovies.model.response.ReviewsResponse;
import ru.gdgkazan.popularmovies.model.response.VideosResponse;
import ru.gdgkazan.popularmovies.network.ApiFactory;
import ru.gdgkazan.popularmovies.screen.loading.LoadingDialog;
import ru.gdgkazan.popularmovies.screen.loading.LoadingView;
import ru.gdgkazan.popularmovies.utils.Images;
import ru.gdgkazan.popularmovies.utils.Videos;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

public class MovieDetailsActivity extends AppCompatActivity {

    private static final String TAG = "MovieDetailsActivity";
    private static final String MAXIMUM_RATING = "10";

    public static final String IMAGE = "image";
    public static final String EXTRA_MOVIE = "extraMovie";

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    @BindView(R.id.layout)
    LinearLayout mLayout;

    @BindView(R.id.reviews)
    TextView mReview;

    @BindView(R.id.toolbar_layout)
    CollapsingToolbarLayout mCollapsingToolbar;

    @BindView(R.id.image)
    ImageView mImage;

    @BindView(R.id.title)
    TextView mTitleTextView;

    @BindView(R.id.overview)
    TextView mOverviewTextView;

    @BindView(R.id.rating)
    TextView mRatingTextView;

    Subscription mReviewSubscription;


    public static void navigate(@NonNull AppCompatActivity activity, @NonNull View transitionImage,
                                @NonNull Movie movie) {
        Intent intent = new Intent(activity, MovieDetailsActivity.class);
        intent.putExtra(EXTRA_MOVIE, movie);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, transitionImage, IMAGE);
        ActivityCompat.startActivity(activity, intent, options.toBundle());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareWindowForAnimation();
        setContentView(R.layout.activity_movie_details);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        ViewCompat.setTransitionName(findViewById(R.id.app_bar), IMAGE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Movie movie = getIntent().getParcelableExtra(EXTRA_MOVIE);
        showMovie(movie);

        LoadingView loadingView = LoadingDialog.view(getSupportFragmentManager());

        Observable<List<Review>> movieReviews = ApiFactory.getMoviesService()
                .getMoviewReview(movie.getId())
                .map(ReviewsResponse::getReviews)
                .flatMap(reviews -> {
                    Realm.getDefaultInstance().executeTransaction(realm -> {
                        realm.delete(Review.class);
                        realm.insert(reviews);
                    });
                    return Observable.just(reviews);
                })
                .onErrorResumeNext(throwable -> {
                    Realm realm = Realm.getDefaultInstance();
                    RealmResults<Review> results = realm.where(Review.class).findAll();
                    return Observable.just(realm.copyFromRealm(results));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

        Observable<List<Video>> movieTrailers = ApiFactory.getMoviesService()
                .getMovieTrailer(movie.getId())
                .map(VideosResponse::getVideos)
                .flatMap(trailers->{
                    Realm.getDefaultInstance().executeTransaction(realm -> {
                    realm.delete(Video.class);
                    realm.insert(trailers);
                    });
                    return Observable.just(trailers);
                })
                .onErrorResumeNext(throwable -> {
                    Realm realm = Realm.getDefaultInstance();
                    RealmResults<Video> results = realm.where(Video.class).findAll();
                    return Observable.just(realm.copyFromRealm(results));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());


        mReviewSubscription = Observable
                .zip(movieReviews, movieTrailers, ReviewAndVideo::new)
                .doOnSubscribe(loadingView::showLoadingIndicator)
                .doAfterTerminate(loadingView::hideLoadingIndicator)
                .subscribe(new Subscriber<ReviewAndVideo>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "onError: "+e);
            }

            @Override
            public void onNext(ReviewAndVideo reviewAndVideo) {
                showReviews(reviewAndVideo.reviewList);
                showTrailers(reviewAndVideo.videoList);
            }
        });


        /**
         * TODO : task
         *
         * Load movie trailers and reviews and display them
         *
         * 1) See http://docs.themoviedb.apiary.io/#reference/movies/movieidtranslations/get?console=1
         * http://docs.themoviedb.apiary.io/#reference/movies/movieidtranslations/get?console=1
         * for API documentation
         *
         * 2) Add requests to {@link ru.gdgkazan.popularmovies.network.MovieService} for trailers and videos
         *
         * 3) Execute requests in parallel and show loading progress until both of them are finished
         *
         * 4) Save trailers and videos to Realm and use cached version when error occurred
         *
         * 5) Handle lifecycle changes any way you like
         */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReviewSubscription!=null){
            mReviewSubscription.unsubscribe();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void prepareWindowForAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Slide transition = new Slide();
            transition.excludeTarget(android.R.id.statusBarBackground, true);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setEnterTransition(transition);
            getWindow().setReturnTransition(transition);
        }
    }

    private void showMovie(@NonNull Movie movie) {
        String title = getString(R.string.movie_details);
        mCollapsingToolbar.setTitle(title);
        mCollapsingToolbar.setExpandedTitleColor(ContextCompat.getColor(this, android.R.color.transparent));

        Images.loadMovie(mImage, movie, Images.WIDTH_780);

        String year = movie.getReleasedDate().substring(0, 4);
        mTitleTextView.setText(getString(R.string.movie_title, movie.getTitle(), year));
        mOverviewTextView.setText(movie.getOverview());

        String average = String.valueOf(movie.getVoteAverage());
        average = average.length() > 3 ? average.substring(0, 3) : average;
        average = average.length() == 3 && average.charAt(2) == '0' ? average.substring(0, 1) : average;
        mRatingTextView.setText(getString(R.string.rating, average, MAXIMUM_RATING));
    }

    private void showTrailers(@NonNull List<Video> videos) {
        // TODO : show trailers
        if (videos.isEmpty()||videos.size()==0){
            return;
        }
        for (int i=0; i<videos.size(); i++){
            TextView textView = new TextView(this);
            textView.setTextSize(22);
            textView.setText(videos.get(i).getName());
            textView.setTag(videos.get(i));
            textView.setOnClickListener(v -> Videos.browseVideo(this, (Video)textView.getTag()));
            mLayout.addView(textView);
        }

    }

    private void showReviews(@NonNull List<Review> reviews) {

        if (reviews.isEmpty() || reviews.size()==0){
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<reviews.size(); i++){
            stringBuilder.append(reviews.get(i).getAuthor());
            stringBuilder.append("\n\n");
            stringBuilder.append(reviews.get(i).getContent());
            stringBuilder.append("\n");
            Log.d(TAG, "showReviews: "+reviews.get(i).getContent());
        }
        mReview.setText(stringBuilder.toString());

    }
}
