
function addRepository() {
    var newRepoApi = "http://localhost:8080/api/" + $('[new-repo-form]').attr('api');

    var repositoryName = document.getElementById("repository-url");

    console.log("Adding repository: " + newRepoApi);
    console.log("Adding repository with value: " + repositoryName.value);

    axios
        .post(newRepoApi, {  name: "someName" })
        .then(function(response) {
            console.log(response)
            console.log("Done adding repository")
           })
        .catch(function(error) {
            console.log(error)
        })
    // We do not want to reload the page
    return false;
}

$(document).ready(function () {
    console.log("Loading page")

    axios.get("http://localhost:8080/api/info", {})
        .then(function (response) {
            console.log(response)
            $("#project-version").text(response.data.projectVersion)
            $("#camel-version").text(response.data.projectVersion)
            $("#kafka-client-version").text(response.data.projectVersion)
        })
        .catch(function (error) {
            console.log(error)

            $("#project-version").text("undefined")
            $("#camel-version").text("undefined")
            $("#kafka-client-version").text("undefined")
        });

    console.log("Did it work?");
})


