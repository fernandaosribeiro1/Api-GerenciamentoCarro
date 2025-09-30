package org.acme;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
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

@Path("/fabricantes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FabricanteResource {

    @GET
    @Operation(
            summary = "Retorna todos os fabricantes (getAll)",
            description = "Retorna uma lista de fabricantes por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class, type = SchemaType.ARRAY)
            )
    )
    public Response getAll(){
        return Response.ok(Fabricante.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um fabricante pela busca por ID (getById)",
            description = "Retorna um fabricante específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    public Response getById(
            @Parameter(description = "Id do fabricante a ser pesquisado", required = true)
            @PathParam("id") long id){
        Fabricante entity = Fabricante.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna os fabricantes conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de fabricantes filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchFabricanteResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome ou detalhes")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de fabricantes por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nome", "detalhes");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Fabricante> query;

        if (q == null || q.isBlank()) {
            query = Fabricante.findAll(sortObj);
        } else {
            query = Fabricante.find(
                    "lower(nome) like ?1 or lower(detalhes) like ?1", sortObj, "%" + q.toLowerCase() + "%");
        }

        List<Fabricante> fabricantes = query.page(effectivePage, size).list();

        var response = new SearchFabricanteResponse();
        response.Fabricantes = fabricantes;
        response.TotalFabricantes = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        URI nextUri = UriBuilder.fromPath("http://localhost:8080/fabricantes/search")
                .queryParam("q", q != null ? q : "")
                .queryParam("page", effectivePage + 1)
                .queryParam("size", size)
                .build();
        response.NextPage = response.HasMore ? nextUri.toString() : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de fabricantes (insert)",
            description = "Adiciona um item à lista de fabricantes por meio de POST e request body JSON. O ID é gerado e retornado na resposta."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    public Response insert(@Valid Fabricante fabricante){
        Fabricante.persist(fabricante);

        URI location = UriBuilder.fromResource(FabricanteResource.class).path("{id}").build(fabricante.id);
        return Response
                .created(location)
                .entity(fabricante)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de fabricantes (delete)",
            description = "Remove um item da lista de fabricantes por meio de Id na URL"
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
            description = "Conflito - Fabricante possui acessórios vinculados"
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Fabricante entity = Fabricante.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        long musicasVinculadas = Acessorio.count("?1 MEMBER OF fabricantes", entity);
        if(musicasVinculadas > 0){
            return Response.status(Response.Status.CONFLICT)
                    .entity("Não é possível deletar o fabricante. Existem " + musicasVinculadas + " acessório(s) vinculado(s).")
                    .build();
        }

        Fabricante.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de fabricantes (update)",
            description = "Edita um item da lista de fabricantes por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Fabricante.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id,@Valid Fabricante newFabricante){
        Fabricante entity = Fabricante.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.nome = newFabricante.nome;
        entity.detalhes = newFabricante.detalhes;

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}