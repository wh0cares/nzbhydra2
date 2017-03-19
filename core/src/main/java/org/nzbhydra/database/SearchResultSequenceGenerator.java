package org.nzbhydra.database;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import java.io.Serializable;

public class SearchResultSequenceGenerator extends SequenceStyleGenerator {
    @Override
    public Serializable generate(SessionImplementor session, Object object) throws HibernateException {
        SearchResultEntity result = (SearchResultEntity) object;
        return (result.getIndexer().getName() + result.getIndexerGuid()).hashCode();

    }
}