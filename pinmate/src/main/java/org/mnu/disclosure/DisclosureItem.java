package org.mnu.disclosure;

public class DisclosureItem {

    private String rcpNo;        // 접수번호
    private String corpName;     // 회사명
    private String stockCode;    // 종목코드
    private String reportTypeCd; // A/B/I
    private String title;        // 공시 제목
    private String receiptDate;  // 접수일자 YYYYMMDD
    private boolean key;         // 핵심 공시 여부

    public String getDartUrl() {
        return "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + rcpNo;
    }

    public String getRcpNo()        { return rcpNo; }
    public void   setRcpNo(String v){ this.rcpNo = v; }

    public String getCorpName()        { return corpName; }
    public void   setCorpName(String v){ this.corpName = v; }

    public String getStockCode()        { return stockCode; }
    public void   setStockCode(String v){ this.stockCode = v; }

    public String getReportTypeCd()        { return reportTypeCd; }
    public void   setReportTypeCd(String v){ this.reportTypeCd = v; }

    public String getTitle()        { return title; }
    public void   setTitle(String v){ this.title = v; }

    public String getReceiptDate()        { return receiptDate; }
    public void   setReceiptDate(String v){ this.receiptDate = v; }

    public boolean isKey()         { return key; }
    public void    setKey(boolean v){ this.key = v; }
}
