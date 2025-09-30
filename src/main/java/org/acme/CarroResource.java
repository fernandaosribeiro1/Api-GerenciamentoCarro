package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Path("/carros")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CarroResource {

    @GET
    @Operation(
            summary = "Retorna todos os carros (getAll)",
            description = "Retorna uma lista de carros por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Carro.class, type = SchemaType.ARRAY)
            )
    )
    public Response getAll(){
        return Response.ok(Carro.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um carro pela busca por ID (getById)",
            description = "Retorna um carro específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Carro.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    public Response getById(
            @Parameter(description = "Id do carro a ser pesquisado", required = true)
            @PathParam("id") long id){
        Carro entity = Carro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna os carros conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de carros filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchCarroResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por modelo ou país de montagem")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de carros por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "modelo", "dataDeFabricacao", "paisDeMontagem");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Carro> query;

        if (q == null || q.isBlank()) {
            query = Carro.findAll(sortObj);
        } else {
            query = Carro.find(
                    "lower(modelo) like ?1 or lower(paisDeMontagem) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<Carro> carros = query.page(effectivePage, size).list();

        var response = new SearchCarroResponse();
        response.Carros = carros;
        response.TotalCarros = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        URI nextUri = URI.create("http://localhost:8080/carros/search?q=" + (q != null ? q : "") + "&page=" + (effectivePage + 1) + (size > 0 ? "&size=" + size : ""));
        response.NextPage = response.HasMore ? nextUri.toString() : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de carros (insert)",
            description = "Adiciona um item à lista de carros por meio de POST e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Carro.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = Carro.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    public Response insert(@Valid Carro carro){
        Carro.persist(carro);

        URI location = URI.create("/carros/" + carro.id);
        return Response
                .created(location)
                .entity(carro)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de carros (delete)",
            description = "Remove um item da lista de carros por meio de Id na URL"
    )
    @APIResponse(
            responseCode = "204",
            description = "Sem conteúdo"
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @APIResponse(
            responseCode = "409",
            description = "Conflito - Carro possui acessórios vinculados",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN,
                    schema = @Schema(implementation = String.class))
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Carro entity = Carro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long musicasVinculadas = Acessorio.count("carro.id = ?1", id);
        if(musicasVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar o carro. Existem " + musicasVinculadas + " acessório(s) vinculado(s).")
                    .build();
        }

        Carro.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de carros (update)",
            description = "Edita um item da lista de carros por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Carro.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Carro.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id, @Valid Carro newCarro){
        Carro entity = Carro.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.modelo = newCarro.modelo;
        entity.nomeCompletoVersao = newCarro.nomeCompletoVersao;
        entity.dataDeFabricacao = newCarro.dataDeFabricacao;
        entity.paisDeMontagem = newCarro.paisDeMontagem;

        if(newCarro.fichaTecnica != null){
            if(entity.fichaTecnica == null){
                entity.fichaTecnica = new FichaTecnica();
            }
            entity.fichaTecnica.detalhesDoMotor = newCarro.fichaTecnica.detalhesDoMotor;
            entity.fichaTecnica.tipoDeCombustivel = newCarro.fichaTecnica.tipoDeCombustivel;
            entity.fichaTecnica.opcionaisDeFabrica = newCarro.fichaTecnica.opcionaisDeFabrica;
        } else {
            entity.fichaTecnica = null;
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}