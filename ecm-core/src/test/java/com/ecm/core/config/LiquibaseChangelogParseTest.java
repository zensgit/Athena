package com.ecm.core.config;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseChangelogParseTest {

    @Test
    @DisplayName("master Liquibase changelog parses with included change files")
    void masterChangelogParses() throws Exception {
        String changelog = "db/changelog/db.changelog-master.xml";
        ClassLoaderResourceAccessor resourceAccessor =
            new ClassLoaderResourceAccessor(Thread.currentThread().getContextClassLoader());

        DatabaseChangeLog parsed = ChangeLogParserFactory.getInstance()
            .getParser(changelog, resourceAccessor)
            .parse(changelog, new ChangeLogParameters(), resourceAccessor);

        assertThat(parsed.getChangeSets()).isNotEmpty();
    }
}
