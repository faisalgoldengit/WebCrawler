-- DROP TABLE IF EXISTS pages CASCADE;
-- DROP TABLE IF EXISTS flyway_schema_history;
CREATE TABLE pages(
    id  BIGSERIAL PRIMARY KEY ,
    url TEXT NOT NULL UNIQUE ,
    title   TEXT NOT NULL,
    content TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS(
        setweight(to_tsvector('english',title),'A') ||
        setweight(to_tsvector('english',content),'B')
    )STORED
);
CREATE INDEX pages_search_index ON pages USING GIN(search_vector);
