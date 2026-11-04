package scad.webservices.soap;

import scad.webservices.soap.generated.CompraRequest;
import scad.webservices.soap.generated.CompraResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class FornecedorSoapEndpoint {

    private static final String NAMESPACE_URI = "http://scad.webservices/soap";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "CompraRequest")
    @ResponsePayload
    public CompraResponse comprar(@RequestPayload CompraRequest request) {
        System.out.println("[SOAP] Pedido recebido:");
        System.out.println("  Produto: " + request.getProduto());
        System.out.println("  Quantidade: " + request.getQuantidade());
        System.out.println("  Fornecedor: " + request.getFornecedor());

        CompraResponse response = new CompraResponse();
        response.setStatus("SUCESSO");
        response.setOrdemId("SOAP-" + System.currentTimeMillis());
        response.setMensagem("Compra SOAP registada: " + request.getQuantidade() + "x " + request.getProduto());
        return response;
    }
}