package org.mnu.disclosure;

public class CorpInfo {

    private String corpCode;
    private String corpName;
    private String stockCode;

    public CorpInfo(String corpCode, String corpName, String stockCode) {
        this.corpCode  = corpCode;
        this.corpName  = corpName;
        this.stockCode = stockCode;
    }

    public String getCorpCode()  { return corpCode;  }
    public String getCorpName()  { return corpName;  }
    public String getStockCode() { return stockCode; }
}
