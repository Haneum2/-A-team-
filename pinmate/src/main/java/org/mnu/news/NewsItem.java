package org.mnu.news;

import org.mnu.news.sentiment.SentimentResult;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NewsItem {

    private String          title;
    private String          source;
    private Date            pubDate;        // java.util.Date — fmt:formatDate 직접 사용 가능
    private String          link;           // 중복제거 키
    private List<String>    relatedStocks = new ArrayList<String>();
    private SentimentResult sentiment;      // 감성 분석 결과 (NewsController에서 설정)

    public String getTitle()                      { return title; }
    public void   setTitle(String title)          { this.title = title; }

    public String getSource()                     { return source; }
    public void   setSource(String source)        { this.source = source; }

    public Date   getPubDate()                    { return pubDate; }
    public void   setPubDate(Date pubDate)        { this.pubDate = pubDate; }

    public String getLink()                       { return link; }
    public void   setLink(String link)            { this.link = link; }

    public List<String> getRelatedStocks()        { return relatedStocks; }
    public void setRelatedStocks(List<String> s)  { this.relatedStocks = s; }

    public SentimentResult getSentiment()                   { return sentiment; }
    public void            setSentiment(SentimentResult sr) { this.sentiment = sr; }
}
