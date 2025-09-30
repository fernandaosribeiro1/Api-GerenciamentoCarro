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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/acessorios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AcessorioResource {

    @GET
    @Operation(
            summary = "Retorna todos os acessórios (getAll)",
            description = "Retorna uma lista de acessórios por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Lista retornada com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class, type = SchemaType.ARRAY)
            )
    )
    public Response getAll(){
        return Response.ok(Acessorio.listAll()).build();
    }

    @GET
    @Path("{id}")
    @Operation(
            summary = "Retorna um acessório pela busca por ID (getById)",
            description = "Retorna um acessório específico pela busca de ID colocado na URL no formato JSON por padrão"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    public Response getById(
            @Parameter(description = "Id do acessório a ser pesquisado", required = true)
            @PathParam("id") long id){
        Acessorio entity = Acessorio.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(entity).build();
    }

    @GET
    @Operation(
            summary = "Retorna os acessórios conforme o sistema de pesquisa (search)",
            description = "Retorna uma lista de acessórios filtrada conforme a pesquisa por padrão no formato JSON"
    )
    @APIResponse(
            responseCode = "200",
            description = "Item retornado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = SearchAcessorioResponse.class)
            )
    )
    @Path("/search")
    public Response search(
            @Parameter(description = "Query de buscar por nome, ano de aquisição ou tempo de instalação")
            @QueryParam("q") String q,
            @Parameter(description = "Campo de ordenação da lista de retorno")
            @QueryParam("sort") @DefaultValue("id") String sort,
            @Parameter(description = "Esquema de filtragem de acessórios por ordem crescente ou decrescente")
            @QueryParam("direction") @DefaultValue("asc") String direction,
            @Parameter(description = "Define qual página será retornada na response")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Define quantos objetos serão retornados por query")
            @QueryParam("size") @DefaultValue("4") int size
    ){
        Set<String> allowed = Set.of("id", "nome", "descricao", "anoAquisicao", "valor", "tempoInstalacaoMinutos");
        if(!allowed.contains(sort)){
            sort = "id";
        }

        Sort sortObj = Sort.by(
                sort,
                "desc".equalsIgnoreCase(direction) ? Sort.Direction.Descending : Sort.Direction.Ascending
        );

        int effectivePage = Math.max(page, 0);

        PanacheQuery<Acessorio> query;

        if (q == null || q.isBlank()) {
            query = Acessorio.findAll(sortObj);
        } else {
            try {
                int numero = Integer.parseInt(q);
                query = Acessorio.find(
                        "anoAquisicao = ?1 or tempoInstalacaoMinutos = ?1",
                        sortObj,
                        numero
                );
            } catch (NumberFormatException e) {
                query = Acessorio.find(
                        "lower(nome) like ?1",
                        sortObj,
                        "%" + q.toLowerCase() + "%"
                );
            }
        }

        List<Acessorio> acessorios = query.page(effectivePage, size).list();

        var response = new SearchAcessorioResponse();
        response.Acessorios = acessorios;
        response.TotalAcessorios = (int) query.count();
        response.TotalPages = query.pageCount();
        response.HasMore = effectivePage < query.pageCount() - 1;
        response.NextPage = response.HasMore ? "http://localhost:8080/acessorios/search?q="+(q != null ? q : "")+"&page="+(effectivePage + 1) + (size > 0 ? "&size="+size : "") : "";

        return Response.ok(response).build();
    }

    @POST
    @Operation(
            summary = "Adiciona um registro à lista de acessórios (insert)",
            description = "Adiciona um item à lista de acessórios por meio de POST e request body JSON. O ID é gerado e retornado na resposta."
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class)
            )
    )
    @APIResponse(
            responseCode = "201",
            description = "Created - Retorna o objeto criado com o ID gerado.",
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Bad Request"
    )
    @Transactional
    public Response insert(@Valid Acessorio acessorio){

        if(acessorio.carro != null && acessorio.carro.id != null){
            Carro a = Carro.findById(acessorio.carro.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Carro com id " + acessorio.carro.id + " não existe").build();
            }
            acessorio.carro = a;
        } else {
            acessorio.carro = null;
        }

        if(acessorio.fabricantes != null && !acessorio.fabricantes.isEmpty()){
            Set<Fabricante> resolved = new HashSet<>();
            for(Fabricante g : acessorio.fabricantes){
                if(g == null || g.id == 0){
                    continue;
                }
                Fabricante fetched = Fabricante.findById(g.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Fabricante com id " + g.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            acessorio.fabricantes = resolved;
        } else {
            acessorio.fabricantes = new HashSet<>();
        }

        Acessorio.persist(acessorio);

        URI location = UriBuilder.fromResource(AcessorioResource.class).path("{id}").build(acessorio.id);
        return Response
                .created(location)
                .entity(acessorio)
                .build();
    }

    @DELETE
    @Operation(
            summary = "Remove um registro da lista de acessórios (delete)",
            description = "Remove um item da lista de acessórios por meio de Id na URL"
    )
    @APIResponse(
            responseCode = "204",
            description = "Sem conteúdo"
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response delete(@PathParam("id") long id){
        Acessorio entity = Acessorio.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        entity.fabricantes.clear();
        entity.persist();

        Acessorio.deleteById(id);
        return Response.noContent().build();
    }

    @PUT
    @Operation(
            summary = "Altera um registro da lista de acessórios (update)",
            description = "Edita um item da lista de acessórios por meio de Id na URL e request body JSON"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Item editado com sucesso",
            content = @Content(
                    schema = @Schema(implementation = Acessorio.class)
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Item não encontrado"
    )
    @Transactional
    @Path("{id}")
    public Response update(@PathParam("id") long id,@Valid Acessorio newAcessorio){
        Acessorio entity = Acessorio.findById(id);
        if(entity == null){
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        entity.nome = newAcessorio.nome;
        entity.descricao = newAcessorio.descricao;
        entity.anoAquisicao = newAcessorio.anoAquisicao;
        entity.valor = newAcessorio.valor;
        entity.tempoInstalacaoMinutos = newAcessorio.tempoInstalacaoMinutos;

        if(newAcessorio.carro != null && newAcessorio.carro.id != null){
            Carro a = Carro.findById(newAcessorio.carro.id);
            if(a == null){
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Carro com id " + newAcessorio.carro.id + " não existe").build();
            }
            entity.carro = a;
        } else {
            entity.carro = null;
        }

        if(newAcessorio.fabricantes != null){
            Set<Fabricante> resolved = new HashSet<>();
            for(Fabricante g : newAcessorio.fabricantes){
                if(g == null || g.id == 0) continue;
                Fabricante fetched = Fabricante.findById(g.id);
                if(fetched == null){
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Fabricante com id " + g.id + " não existe").build();
                }
                resolved.add(fetched);
            }
            entity.fabricantes = resolved;
        } else {
            entity.fabricantes = new HashSet<>();
        }

        return Response.status(Response.Status.OK).entity(entity).build();
    }
}