package com.sergio.trackmyshow.activities;

import android.content.Intent;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.sergio.trackmyshow.R;
import com.sergio.trackmyshow.adapter.RecyclerViewItemClick;
import com.sergio.trackmyshow.adapter.SeasonAdapter;
import com.sergio.trackmyshow.api.tmdb.ImageGetter;
import com.sergio.trackmyshow.api.tmdb.TMDBService;
import com.sergio.trackmyshow.models.tmdb.Season;
import com.sergio.trackmyshow.models.tmdb.TVShow;
import com.sergio.trackmyshow.models.tmdb.TVShowSearch;
import com.sergio.trackmyshow.util.DBUtil;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TvShowActivity extends AppCompatActivity {
    private ViewGroup mRootView, mContentView;
    private ImageView imgPoster, imgBackdrop;
    private TextView tvOverview, tvRelease, tvGenres, tvStatus;
    private RecyclerView rvSeasons;
    private ProgressBar mProgressBar;
    private TVShowSearch mTVShowSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_show);
        Toolbar toolbar = (Toolbar) findViewById(R.id.show_toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mRootView = (ViewGroup) findViewById(R.id.activity_tv_show);
        mContentView = (ViewGroup) findViewById(R.id.scroll_view_show);
        imgPoster = (ImageView) findViewById(R.id.img_show_poster);
        imgBackdrop = (ImageView) findViewById(R.id.img_show_backdrop);
        tvOverview = (TextView) findViewById(R.id.tv_show_overview);
        tvRelease = (TextView) findViewById(R.id.tv_show_release);
        tvGenres = (TextView) findViewById(R.id.tv_show_genres);
        tvStatus = (TextView) findViewById(R.id.tv_show_status);
        rvSeasons = (RecyclerView) findViewById(R.id.rv_show_seasons);
        rvSeasons.setVisibility(View.INVISIBLE);
        addProgressBar();

        Bundle extras = getIntent().getExtras();
        TVShowSearch ts = extras.getParcelable("TVShowSearch");
        TVShow t = extras.getParcelable("TVShow");
        mTVShowSearch = ts;
        if (t == null && ts != null) {
            setTitle(ts.getName());
            getTvShowInformation(ts);
        } else if (t != null && ts != null) {
            setTitle(ts.getName());
            setTvShowInfo(t, ts);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        DBUtil dbUtil = new DBUtil(this);
        boolean result = dbUtil.findTvShow(mTVShowSearch.getId());

        menu.setGroupVisible(R.id.action_group_delete, result);
        menu.setGroupVisible(R.id.action_group_save, !result);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_delete:
                String result = new DBUtil(this).deleteTvShow(mTVShowSearch.getId());
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void getTvShowInformation(final TVShowSearch ts) {
        TMDBService tmdbService = new TMDBService.Builder().build();
        Call<TVShow> call = tmdbService.getShowInfo(ts.getId());

        call.enqueue(new Callback<TVShow>() {
            @SuppressWarnings("deprecation")
            @Override
            public void onResponse(Call<TVShow> call, Response<TVShow> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(TvShowActivity.this, "Error: " + response.message(), Toast.LENGTH_SHORT)
                            .show();
                    Log.e("URL: ", call.request().url().toString());
                } else {
                    TVShow t = response.body();
                    setTvShowInfo(t, ts);
                }
            }

            @Override
            public void onFailure(Call<TVShow> call, Throwable t) {
                Toast.makeText(TvShowActivity.this, "Error: " + t.getLocalizedMessage(),
                        Toast.LENGTH_SHORT)
                        .show();
                Log.e("Error: TvShowActivity", t.getMessage());
            }
        });
    }

    private void setTvShowInfo(TVShow t, TVShowSearch ts) {
        String releaseDate = "<b>Release date: </b>" + ts.getReleaseDate();
        String status = "<b>Status: </b>" + t.getStatus();
        String genres = "<b>Genres: </b>" + t.getGenresString();

        for (Season season : t.getSeasonList()) {
            int num = season.getNumber();
            String name;
            if (num == 0) {
                name = getResources().getString(R.string.special_season);
            } else {
                name = getResources().getString(R.string.season);
                name = name + " " + num;
            }
            season.setTempName(name);
        }

        tvOverview.setText(t.getOverview());
        tvGenres.setText(Html.fromHtml(genres));
        tvRelease.setText(Html.fromHtml(releaseDate));
        tvStatus.setText(Html.fromHtml(status));
        setupRecyclerView((ArrayList<Season>) t.getSeasonList(), ts, t);

        removeProgressBar();
        new ImageGetter(t.getBackdropPath(), "backdrop", imgBackdrop);
        new ImageGetter(ts.getPosterPath(), "poster", imgPoster);
    }

    private void addProgressBar() {
        mContentView.setVisibility(View.INVISIBLE);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        mProgressBar = new ProgressBar(this);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setLayoutParams(params);
        mRootView.addView(mProgressBar);
    }

    private void removeProgressBar() {
        mRootView.removeView(mProgressBar);
        mContentView.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerView(final ArrayList<Season> seasonList, final TVShowSearch ts, final TVShow t) {
        SeasonAdapter adapter = new SeasonAdapter(seasonList, getResources());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSeasons.setAdapter(adapter);
        rvSeasons.setLayoutManager(layoutManager);
        rvSeasons.setHasFixedSize(true);
        rvSeasons.setNestedScrollingEnabled(false);
        rvSeasons.setFocusable(false);
        rvSeasons.setVisibility(View.VISIBLE);
        rvSeasons.addOnItemTouchListener(new RecyclerViewItemClick(this, new RecyclerViewItemClick.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Intent i = new Intent(TvShowActivity.this, SeasonActivity.class);
                Bundle extras = new Bundle();
                extras.putParcelable("paramTVShow", t);
                extras.putParcelableArrayList("paramSeasonList", seasonList);
                extras.putParcelable("paramSeason", seasonList.get(position));
                extras.putParcelable("paramTVShowSearch", ts);
                i.putExtras(extras);
                startActivity(i);
            }
        }));
    }
}
