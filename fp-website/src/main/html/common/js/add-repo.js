function addRepository() {
    var newRepoApi = "http://localhost:8080/api/" + $('[new-repo-form]').attr('api');

    var repositoryName = document.getElementById("repository-url")

    console.log("Adding repository: " + newRepoApi)
    console.log("Adding repository with value: " + repositoryName.value)

    axios
            .get(newRepoApi)
            .then(function(response) {
                console.log(response)
                console.log("Done adding repository")
               })
            .catch(function(error) {
                console.log(error)
            })

//    axios
//        .post(newRepoApi, {  name: "someName" })
//        .then(function(response) {
//            console.log(response)
//            console.log("Done adding repository")
//           })
//        .catch(function(error) {
//            console.log(error)
//        })
}

$(document).ready(function () {
    console.log("Loading page")
})


