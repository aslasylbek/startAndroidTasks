package ru.gdgkazan.popularmovies.model.content;

import java.util.List;

public class ReviewAndVideo {



    public List<Review> reviewList;

    public List<Video> videoList;

    public ReviewAndVideo(List<Review> reviewList, List<Video> videoList) {
        this.reviewList = reviewList;
        this.videoList = videoList;
    }
}
