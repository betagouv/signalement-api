CREATE TABLE IF NOT EXISTS albert_classification (
                                                  report_id UUID PRIMARY KEY NOT NULL,
                                                  category text,
                                                  confidence_score real,
                                                  explanation text,
                                                  summary text,
                                                  raw text NOT NULL,
                                                  code_conso text,
                                                  CONSTRAINT fk_reportId FOREIGN KEY (report_id) REFERENCES reports(id)
    );