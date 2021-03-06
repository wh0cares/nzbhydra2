package org.nzbhydra.searching;

import org.nzbhydra.config.IndexerConfig;
import org.nzbhydra.indexers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SearchModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(SearchModuleProvider.class);

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private IndexerRepository indexerRepository;
    @Autowired
    private IndexerSearchRepository indexerSearchRepository;
    @Autowired
    private IndexerApiAccessRepository indexerApiAccessRepository;

    private Map<String, Indexer> searchModuleInstances = new HashMap<>();

    @Autowired
    private List<IndexerHandlingStrategy> indexerHandlingStrategies;


    public List<Indexer> getIndexers() {
        return new ArrayList<>(searchModuleInstances.values());
    }

    public List<Indexer> getEnabledIndexers() {
        return searchModuleInstances.values().stream().filter(x -> x.getConfig().getState() == IndexerConfig.State.ENABLED).collect(Collectors.toList());
    }

    public Indexer getIndexerByName(String indexerName) {
        if (searchModuleInstances.containsKey(indexerName)) {
            return searchModuleInstances.get(indexerName);
        } else {
            throw new RuntimeException("Unable to find indexer with name " + indexerName);
        }
    }


    /**
     * Must be called by <tt>{@link SearchModuleConfigProvider}</tt> when config is loaded.
     */
    @Transactional
    public void loadIndexers(List<IndexerConfig> indexers) {
        if (indexers == null) {
            logger.error("Indexers not set. Check your configuration");
            return;
        }
        logger.info("Loading indexers");
        searchModuleInstances.clear();
        for (IndexerConfig config : indexers) {
            try {
                Optional<IndexerHandlingStrategy> optionalStrategy = indexerHandlingStrategies.stream().filter(x -> x.handlesIndexerConfig(config)).findFirst();
                if (!optionalStrategy.isPresent()) {
                    logger.error("Unable to find implementation for indexer type {} and host {}", config.getSearchModuleType(), config.getHost());
                    continue;
                }

                Indexer searchModule = beanFactory.createBean(optionalStrategy.get().getIndexerClass());
                logger.info("Initializing indexer {}", config.getName());

                IndexerEntity indexerEntity = indexerRepository.findByName(config.getName());
                if (indexerEntity == null) {
                    logger.info("Indexer with name {} not yet in database. Adding it", config.getName());
                    indexerEntity = new IndexerEntity();
                    indexerEntity.setName(config.getName());
                    indexerEntity = indexerRepository.save(indexerEntity);
                    logger.info("Now {} indexers in database", indexerRepository.count());
                }

                searchModule.initialize(config, indexerEntity);
                searchModuleInstances.put(config.getName(), searchModule);
            } catch (Exception e) {
                logger.error("Unable to instantiate indexer with name {} and type {}", config.getName(), config.getSearchModuleType(), e);
            }
        }
        List<String> indexerNames = indexers.stream().map(IndexerConfig::getName).collect(Collectors.toList());
        Collection<IndexerEntity> byNameNotIn = indexerRepository.findByNameNotIn(indexerNames);
        if (!byNameNotIn.isEmpty()) {
            logger.info("Found {} indexers in database which are not configured. Will delete them and any related database entries. This may take some time", byNameNotIn.size());
            indexerRepository.delete(byNameNotIn);
        }
        if (searchModuleInstances.isEmpty()) {
            logger.warn("No indexers configured");
        }
    }
}
