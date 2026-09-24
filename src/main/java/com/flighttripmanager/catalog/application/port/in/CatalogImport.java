package com.flighttripmanager.catalog.application.port.in;

import com.flighttripmanager.catalog.application.contract.CatalogImportBatch;
import com.flighttripmanager.catalog.application.contract.CatalogImportResult;

public interface CatalogImport {
    CatalogImportResult importBatch(CatalogImportBatch batch);
}
