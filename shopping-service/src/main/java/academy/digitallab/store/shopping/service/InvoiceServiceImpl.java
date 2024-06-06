package academy.digitallab.store.shopping.service;

import org.springframework.transaction.annotation.Transactional;
import academy.digitallab.store.shopping.client.CustomerClient;
import academy.digitallab.store.shopping.client.ProductClient;
import academy.digitallab.store.shopping.entity.InvoiceItem;
import academy.digitallab.store.shopping.model.Customer;
import academy.digitallab.store.shopping.model.Product;
import academy.digitallab.store.shopping.repository.InvoiceItemsRepository;
import academy.digitallab.store.shopping.repository.InvoiceRepository;
import academy.digitallab.store.shopping.entity.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;


import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    InvoiceItemsRepository invoiceItemsRepository;
    @Autowired
    CustomerClient customerClient;

    @Autowired
    ProductClient productClient;

    @Override
    public List<Invoice> findInvoiceAll() {
        return  invoiceRepository.findAll();
    }


    @Override
    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        Invoice invoiceDB = invoiceRepository.findByNumberInvoice(invoice.getNumberInvoice());
        if (invoiceDB != null) {
            return invoiceDB;
        }
        invoice.setState("CREATED");

        invoice.getItems().forEach(invoiceItem -> {
            Product product = productClient.getProduct(invoiceItem.getProductId()).getBody();
            if (product != null) {
                invoiceItem.setPrice(product.getPrice());
            }
        });

        invoiceDB = invoiceRepository.save(invoice);

        try {
            invoiceDB.getItems().forEach(invoiceItem -> {
                productClient.updateStockProduct(invoiceItem.getProductId(), invoiceItem.getQuantity() * -1);
            });
        } catch (RestClientException e) {
            // Handle the error and rollback the transaction manually
            throw new RuntimeException("Error updating product stock", e);
        }

        return invoiceDB;
    }

    @Override
    public Invoice updateInvoice(Invoice invoice) {

		invoice.getItems().forEach(invoiceItem -> {
            Product product = productClient.getProduct(invoiceItem.getProductId()).getBody();
            if (product != null) {
                invoiceItem.setPrice(product.getPrice());
            }
        });
		
        Invoice invoiceDB = getInvoice(invoice.getId());
        if (invoiceDB == null){
            return  null;
        }
        invoiceDB.setCustomerId(invoice.getCustomerId());
        invoiceDB.setDescription(invoice.getDescription());
        invoiceDB.setNumberInvoice(invoice.getNumberInvoice());
        invoiceDB.getItems().clear();
        invoiceDB.setItems(invoice.getItems());
        return invoiceRepository.save(invoiceDB);
    }


    @Override
    public Invoice deleteInvoice(Invoice invoice) {
        Invoice invoiceDB = getInvoice(invoice.getId());
        if (invoiceDB == null){
            return  null;
        }
        invoiceDB.setState("DELETED");
        return invoiceRepository.save(invoiceDB);
    }

    @Override
    public Invoice getInvoice(Long id) {

        Invoice invoice= invoiceRepository.findById(id).orElse(null);
        if (null != invoice ){
            Customer customer = customerClient.getCustomer(invoice.getCustomerId()).getBody();
            invoice.setCustomer(customer);
            List<InvoiceItem> listItem=invoice.getItems().stream().map(invoiceItem -> {
                Product product = productClient.getProduct(invoiceItem.getProductId()).getBody();
                invoiceItem.setProduct(product);
                return invoiceItem;
            }).collect(Collectors.toList());
            invoice.setItems(listItem);
        }
        return invoice ;
    }
}