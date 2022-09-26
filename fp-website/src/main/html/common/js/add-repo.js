var completed = false;

function renderStates(tracking) {
    for (let i in tracking.states) {
        if (tracking.states[i] == "cloning") {
            UIkit.notification('Cloning repository');
        }

        if (tracking.states[i] == "pulling") {
            UIkit.notification('Pulling repository');
        }

        if (tracking.states[i] == "reading-log") {
            UIkit.notification('Reading commit log');

            completed = true;
            return;
        }
    }
}

function updateStates(transactionId) {
    axios.get("http://localhost:8080/api/tracking/" + transactionId, {})
            .then(function (response) {
                console.log(response)
                renderStates(response.data);
            })
            .catch(function (error) {
                console.log(error)
            });

}


function addRepository() {
    var newRepoApi = "http://localhost:8080/api/" + $('[new-repo-form]').attr('api');

    var repositoryName = document.getElementById("repository-url");
    console.debug("Adding repository to: " + newRepoApi);
    console.debug("Adding repository with value: " + repositoryName.value);

    $("#wait-for-repo-spinner").show();
    $("#search-form").fadeOut()
    axios
        .post(newRepoApi, {
            name: repositoryName.value
        })
        .then(function(response) {
            console.debug(response)
            console.debug("Done adding repository: " + response.data.state)
            completed = false;

            if (response.data.state == "OK") {
                var count = 0;
                var timeout = setInterval(function() {
                    updateStates(response.data.transactionId);
                    count++;

                    if (completed == true || count > 60) {
                        clearInterval(timeout)
                        $("#search-form").fadeIn();
                        $("#wait-for-repo-spinner").fadeOut();
                    }
                }, 1000)

            }
           })
        .catch(function(error) {
            console.log(error)
        })
        console.log("Done adding to repo")
    // We do not want to reload the page
    return false;
}

$(document).ready(function () {
    console.debug("Loading page")

    $("#wait-for-repo-spinner").hide();

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


