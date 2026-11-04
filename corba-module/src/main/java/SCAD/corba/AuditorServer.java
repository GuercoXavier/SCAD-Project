package SCAD.corba;

import SCAD.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

public class AuditorServer {

    static class AuditorImpl extends AuditoriaPOA {
        @Override
        public int consultarSaldo(String produto) {
            System.out.println("[AUDITOR] Consulta de saldo para: " + produto);
            return 42; // Simulação
        }

        @Override
        public void dispararAlertaStock(String produto, int quantidadeAtual) {
            System.out.println("[AUDITOR] ⚠️ ALERTA: " + produto + " com stock baixo (" + quantidadeAtual + ")");
        }

        @Override
        public void registarCompra(String produto, int quantidade, String fornecedor) {
            System.out.println("[AUDITOR] Compra registada: " + quantidade + "x " + produto + " de " + fornecedor);
        }
    }

    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            AuditorImpl auditorImpl = new AuditorImpl();
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(auditorImpl);
            Auditoria auditor = AuditoriaHelper.narrow(ref);

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            NameComponent[] path = ncRef.to_name("SCAD.Auditoria");
            ncRef.rebind(path, auditor);

            System.out.println("✅ [AUDITOR] Servidor CORBA pronto como 'SCAD.Auditoria'");
            orb.run();
        } catch (Exception e) {
            System.err.println("Erro no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}