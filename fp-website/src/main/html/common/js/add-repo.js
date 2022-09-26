
function addRepository() {
    var newRepoApi = "http://localhost:8080/api/" + $('[new-repo-form]').attr('api');

    var repositoryName = document.getElementById("repository-url");
    console.debug("Adding repository to: " + newRepoApi);
    console.debug("Adding repository with value: " + repositoryName.value);

    axios
        .post(newRepoApi, {
            name: repositoryName.value
        })
        .then(function(response) {
            console.debug(response)
            console.debug("Done adding repository: " + response.data.state)
           })
        .catch(function(error) {
            console.log(error)
        })
    // We do not want to reload the page
    return false;
}

$(document).ready(function () {
    console.debug("Loading page")

    axios.get("http://localhost:8080/api/info", {})
        .then(function (response) {
            console.log(response)
            $("#project-version").text(response.data.projectVersion)
            $("#camel-version").text(response.data.camelVersion)
            $("#kafka-client-version").text(response.data.kafkaClientVersion)
        })
        .catch(function (error) {
            console.log(error)

            $("#project-version").text("undefined")
            $("#camel-version").text("undefined")
            $("#kafka-client-version").text("undefined")
        });
})


